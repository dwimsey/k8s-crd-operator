package us.wimsey.kubernetes.operators.crd.kubernetes;

//import com.openshift.restclient.model.volume.IPersistentVolumeClaim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionStatus;
import io.kubernetes.client.util.Watch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;

public class CRDChangeNotification<T> {
	private static final Logger LOG = LoggerFactory.getLogger(CRDChangeNotification.class);



	@SerializedName("apiVersion")
	private String apiVersion = null;

	@SerializedName("kind")
	private String kind = null;

	@SerializedName("metadata")
	private V1ObjectMeta metadata = null;

	@SerializedName("status")
	private V1Status status = null;

	public String getApiVersion() {
		return apiVersion;
	}

	public void setApiVersion(String apiVersion) {
		this.apiVersion = apiVersion;
	}

	public String getKind() {
		return kind;
	}

	public void setKind(String kind) {
		this.kind = kind;
	}

	public V1ObjectMeta getMetadata() {
		return metadata;
	}

	public void setMetadata(V1ObjectMeta metadata) {
		this.metadata = metadata;
	}

	public V1Status getStatus() {
		return status;
	}

	public void setStatus(V1Status status) {
		this.status = status;
	}

	@Override
	public boolean equals(java.lang.Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		CRDChangeNotification<T> otherCRDChangeNotification = (CRDChangeNotification<T>) o;
		return Objects.equals(this.apiVersion, otherCRDChangeNotification.apiVersion) &&
			Objects.equals(this.kind, otherCRDChangeNotification.kind) &&
			Objects.equals(this.metadata, otherCRDChangeNotification.metadata) &&
			Objects.equals(this.spec, otherCRDChangeNotification.spec) &&
			Objects.equals(this.status, otherCRDChangeNotification.status);
	}

	@Override
	public int hashCode() {
		return Objects.hash(apiVersion, kind, metadata, spec, status);
	}


	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("class V1AlertConfigSpec {\n");

		sb.append("    apiVersion: ").append(toIndentedString(apiVersion)).append("\n");
		sb.append("    kind: ").append(toIndentedString(kind)).append("\n");
		sb.append("    metadata: ").append(toIndentedString(metadata)).append("\n");
		sb.append("    spec: ").append(toIndentedString(spec)).append("\n");
		sb.append("    status: ").append(toIndentedString(status)).append("\n");
		sb.append("}");
		return sb.toString();
	}

	/**
	 * Convert the given object to string with each line indented by 4 spaces
	 * (except the first line).
	 */
	private String toIndentedString(java.lang.Object o) {
		if (o == null) {
			return "null";
		}
		return o.toString().replace("\n", "\n    ");
	}
























	final private String type;
	public String getType() {
		return type;
	}

	public T getSpec() {
		return spec;
	}

	final private T spec;

	public CRDChangeNotification(String type, V1Status status, String apiVersion, String kind, V1ObjectMeta metadata, T spec) {
		this.type = type;
		this.apiVersion = apiVersion;
		this.kind = kind;
		this.metadata = metadata;
		this.spec = spec;
		this.status = status;
	}

	boolean Equals(Object o) {
		if (o == this) {
			return true;
		}

		if (!this.getClass().equals(o.getClass())) {
			return false;
		}

		CRDChangeNotification<T> otherCRDChangeNotification = (CRDChangeNotification<T>)o;

		return true;
	}

	static <K> CRDChangeNotification<K> from(ApiClient client, Watch.Response<Object> watchNotification, Type typeOfSrc) throws IOException {
		if(watchNotification.object == null) {
			return null;
		}

		Map<String, Object> watchObj = (Map<String, Object>)watchNotification.object;
		V1ObjectMeta metadata = client.getJSON().deserialize(client.getJSON().serialize(((Map<String, Object>)watchNotification.object).get("metadata")), V1ObjectMeta.class);
		String apiVersion = (String)watchObj.get("apiVersion");
		String kind = (String)watchObj.get("kind");

		// Return the object in its native form
		if(typeOfSrc.equals(Object.class) || typeOfSrc.equals(watchNotification.object.getClass())) {
			return new CRDChangeNotification(watchNotification.type, watchNotification.status, apiVersion, kind, metadata, watchNotification.object);
		}

		// Variations on type conversion
		if(typeOfSrc.equals(String.class)) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			return new CRDChangeNotification(watchNotification.type, watchNotification.status, apiVersion, kind, metadata, gson.toJson(watchNotification.object));
		} else if(typeOfSrc.equals(JsonElement.class)) {
			Gson gson = client.getJSON().getGson();
			return new CRDChangeNotification(watchNotification.type, watchNotification.status, apiVersion, kind, metadata, gson.toJsonTree(watchNotification.object));
		} else {
			Gson gson = client.getJSON().getGson();
			K spec = gson.fromJson(gson.toJsonTree(((Map<String, Object>) watchNotification.object).get("spec")), typeOfSrc);
			return new CRDChangeNotification<K>(watchNotification.type, watchNotification.status, apiVersion, kind, metadata, spec);
		}
	}
}
