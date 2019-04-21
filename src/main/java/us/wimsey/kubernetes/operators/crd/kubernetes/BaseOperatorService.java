package us.wimsey.kubernetes.operators.crd.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.reflect.TypeToken;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.models.*;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CollectionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BaseOperatorService<T> implements InitializingBean, DisposableBean, ApplicationContextAware, BeanNameAware {
	private static final Logger LOG = LoggerFactory.getLogger(BaseOperatorService.class);


	public BaseOperatorService() {
		LOG.trace("Creating BaseOperatorService derived service");
	}

	private static final String CRD_CHANGE_NOTIFICATION_QUEUE_NAME = "crdChangeEventQueue";

	@Value("${kubernetes.service.scheme:https}")
	private String kubernetesServiceScheme;

	@Value("${kubernetes.service.validatessl:true}")
	private Boolean kubernetesServiceValidateSSL;

	@Value("${kubernetes.service.host:kubernetes.default.svc.cluster.local}")
	private String kubernetesServiceHost;

	@Value("${kubernetes.service.port:443}")
	private String kubernetesServicePort;

	@Value("${kubernetes.service.username:}")
	private String kubernetesServiceUsername;

	@Value("${kubernetes.service.token:/var/run/secrets/kubernetes.io/serviceaccount/token}")
	private String kubernetesServiceToken;

	@Value("${kubernetes.pollsleepms:500}")
	private int watcherPollSleepTime;

	@Value("${kubernetes.readtimeout:360000}")
	private long watcherReadTimeout;

	@Value("${distributed.mode.disabled:false}")
	private Boolean igniteDisabled;

	@Value("${distributed.mode.configuration.file:classpath:ignite.xml}")
	private String igniteConfigurationFile;

	@Value("${distributed.mode.backup.count:0}")
	private Integer igniteReplicationBackup;

	@Value("${distributed.mode.queue.size:0}")
	private Integer igniteQueueSize;

	private Queue<CRDChangeNotification> crdChangeNotificationQueue;


	protected ApplicationContext applicationContext = null;
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	protected String beanName = null;
	public void setBeanName(String name) {
		this.beanName = name;
	}
	private ApiClient getAuthenticatedApiClient() throws IOException {
		String openShiftPassword;
		if (kubernetesServiceToken.startsWith("/")) {
			// If the password starts with /, it is expected
			// to be a filename pointing to a token.  Basically
			// this means passwords can't start with '/', but so what? --dwimsey

			// we re-read this every time, just in case it changes this way we get new tokens
			// as needed if we get disconnected due to a bad token (or any other reason)
			openShiftPassword = new String(Files.readAllBytes(Paths.get(kubernetesServiceToken)));
		} else {
			openShiftPassword = kubernetesServiceToken;
		}

		String openShiftUrl = kubernetesServiceScheme + "://" + kubernetesServiceHost + ":" + kubernetesServicePort;

		ApiClient client;
		if (kubernetesServiceUsername != null && !kubernetesServiceUsername.isEmpty()) {
			LOG.debug("Using username/password authentication: {}", kubernetesServiceUsername);
			client = Config.fromUserPassword(openShiftUrl, kubernetesServiceUsername, openShiftPassword);
		} else {
			LOG.debug("Using BearerToken: {}", kubernetesServiceToken);
			client = Config.fromToken(openShiftUrl, openShiftPassword);
		}

		LOG.debug("Verify SSL Certificates: {}", kubernetesServiceValidateSSL);
		client.setVerifyingSsl(kubernetesServiceValidateSSL);

		LOG.debug("Kubernetes Watch Read Timeout: {}", watcherReadTimeout);
		client.getHttpClient().setReadTimeout(watcherReadTimeout, TimeUnit.SECONDS);
		return client;
	}

	@Override
	public void afterPropertiesSet() throws Exception {

		// If we set the replication count to 0, meaning no replicas, then just disable ignite all together.
		// If the replication count is less than 0, require all nodes to be synchronized
		// If the replication count is greater than 0, then require that many backup ignite nodes.  This can be a problem if the cluster contains too few pods running
		if (!igniteDisabled) {
			Ignite ignite = Ignition.start(igniteConfigurationFile);
			CollectionConfiguration cfg = new CollectionConfiguration();
			if(igniteReplicationBackup > 0) {
				cfg.setCacheMode(CacheMode.PARTITIONED);
				cfg.setBackups(igniteReplicationBackup);
			} else {
				cfg.setCacheMode(CacheMode.REPLICATED);
			}
			crdChangeNotificationQueue = ignite.queue(CRD_CHANGE_NOTIFICATION_QUEUE_NAME, igniteQueueSize, cfg);
		} else {
			crdChangeNotificationQueue = new LinkedBlockingQueue<>();
		}

		new Thread(new Runnable() {
			@Override
			public void run() {
				startSpringServiceManagerThread();
			}
		}).start();
	}

	@Override
	public void destroy() throws Exception {
		beanShouldStop.set(true);
	}

	private AtomicBoolean beanShouldStop = new AtomicBoolean(false);
	Thread crdChangeWatcherServiceThread = null;
	Thread crdChangeNotificationServiceThread = null;

	final ObjectMapper mapper = new ObjectMapper();
	private void startSpringServiceManagerThread() {
		crdChangeWatcherServiceThread = new Thread(new Runnable() {
			@Override
			public void run() {
				String resourceVersion = null;

				while(!beanShouldStop.get()) {
					try {
						ApiClient client = getAuthenticatedApiClient();

						CustomObjectsApi api = new CustomObjectsApi(client);

						try (Watch<Object> watch = Watch.createWatch(client,
							api.listNamespacedCustomObjectCall(getCrdVersionGroupName(), getCrdVersionApi(), getCrdNamespaceScope(), getCrdPlural(), null, null, resourceVersion, true, null, null),
							new TypeToken<Watch.Response<Object>>(){}.getType())) {
							for (Watch.Response<Object> crdChangeNotification : watch) {
								if(crdChangeNotification.object instanceof Map) {
									final String kind = ((Map<String, String>)crdChangeNotification.object).get("kind");
									LOG.trace("CRD Object Kind: " + kind);
									switch(crdChangeNotification.type) {
										case "ERROR":
											switch(kind) {
												case "Status":
													V1Status status = client.getJSON().deserialize(client.getJSON().serialize(crdChangeNotification.object), V1Status.class);
													if(status.getCode() == 410) {
														resourceVersion = null;
													}
													LOG.info("Status update: " + status.getMessage());
													break;
												default:
													LOG.info("CRD Error of kind: " + kind + ": " + client.getJSON().serialize(crdChangeNotification.object.toString()));
													break;
											}
											break;
										default:
											CRDChangeNotification<T> crd = CRDChangeNotification.from(client, crdChangeNotification, getGenericClass());

											// Handle finalizer work
											List<String> a = crd.getMetadata().getFinalizers();
											if(a != null && a.size() > 0) {
												/// See if we're in the finalizer list
												LOG.info("Remaining finalizers: ");
												for(String finalizerLocator : a) {
													if(finalizerLocator.equals("test.finalizer.wimsey")) {
														LOG.info("Needs deleting: " + finalizerLocator);

														ArrayList<String> newFinalizers = new ArrayList<>(a.size());
														newFinalizers.addAll(a);
														newFinalizers.remove(finalizerLocator);


													}
													LOG.info(finalizerLocator);
												}
											}

											// Have the abstract class do its thing
											try {
												processCrdChangeEvent(client, crd);
											} catch (Throwable t) {
												LOG.error("Caught throwable: " + t.getMessage());
											}
											// Remember which version we last processed so we don't process it again
											if(crd != null && crd.getMetadata() != null && crd.getMetadata().getResourceVersion() != null) {
												resourceVersion = crd.getMetadata().getResourceVersion();
											}
											break;

									}
								} else {
									LOG.error("Unknown type of object returned: " + crdChangeNotification.object.getClass().toString());
									throw new RuntimeException();
								}
							}
						}

					} catch(InterruptedIOException ie) {
						LOG.info("CRD Change Watcher interrupted");
						// We get interrupted when destroy() is called, so skip the delay below
						continue;
					} catch(ApiException e) {
						if(e.getCode()==403) {
							LOG.error("Unhandled API exception in CRD Change Watcher (CRDCW): exiting: " + e);
							if (applicationContext != null) {
								((ConfigurableApplicationContext)applicationContext).close();
							}
						} else {
							LOG.error("Unhandled API exception in CRD Change Watcher (CRDCW): " + e);
						}
					} catch(Exception e) {
						LOG.error("Unhandled exception in CRD Change Watcher (CRDCW): " + e);
					}

					try {
						Thread.sleep(watcherPollSleepTime);
					} catch (InterruptedException e) {
						LOG.info("Change Watcher Interrupted Sleep: " + e);
					}
				}
				LOG.info("CRD Change Watcher is stopped.");
			}
		});

		crdChangeNotificationServiceThread = new Thread(new Runnable() {
			@Override
			public void run() {
				ApiClient client = null;
				while(!beanShouldStop.get()) {
					try {
						if(client == null) {
							client = getAuthenticatedApiClient();
						}
						CRDChangeNotification CRDChangeNotification = crdChangeNotificationQueue.poll();
						while(CRDChangeNotification != null) {
							LOG.trace("CRDChangeNotification dequeued: {}", CRDChangeNotification.toString());
							//processPvcChange(client, CRDChangeNotification);
							CRDChangeNotification = crdChangeNotificationQueue.poll();
						}

					} catch(Exception e) {
						LOG.error("Unhandled exception in CRD Notification Manager (CRDNMW): " + e);
						client = null;
					}

					try {
						Thread.sleep(watcherPollSleepTime);
					} catch (InterruptedException e) {
						LOG.trace("Claim Notification Watcher Interrupted Sleep: " + e);
					}
				}
				LOG.info("CRD Change Handler thread is stopped.");
			}
		});

		crdChangeNotificationServiceThread.start();
		crdChangeWatcherServiceThread.start();
	}

	protected abstract String getCrdPlural();

	protected abstract String getCrdNamespaceScope();

	protected abstract String getCrdVersionApi();

	protected abstract String getCrdVersionGroupName();


	private Class<T> inferedClass;

	public Class<T> getGenericClass() throws ClassNotFoundException {
		if(inferedClass == null){
			Type mySuperclass = getClass().getGenericSuperclass();
			Type tType = ((ParameterizedType)mySuperclass).getActualTypeArguments()[0];
			String className = tType.toString().split(" ")[1];
			inferedClass = (Class<T>) Class.forName(className);
		}
		return inferedClass;
	}

	abstract void processCrdChangeEvent(ApiClient client, CRDChangeNotification<T> crdChangeNotification);
}
