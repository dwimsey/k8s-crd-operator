package us.wimsey.kubernetes.operators.crd.kubernetes;

import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionStatus;

public class V1AbstructCustomResourceDefinitionObject {

	@SerializedName("apiVersion")
	private String apiVersion = null;

	@SerializedName("kind")
	private String kind = null;

	@SerializedName("metadata")
	private V1ObjectMeta metadata = null;

	@SerializedName("status")
	private V1beta1CustomResourceDefinitionStatus status = null;

	public V1AbstructCustomResourceDefinitionObject(String apiVersion) {
		this.apiVersion = apiVersion;
	}

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

	public V1beta1CustomResourceDefinitionStatus getStatus() {
		return status;
	}

	public void setStatus(V1beta1CustomResourceDefinitionStatus status) {
		this.status = status;
	}

}
