package com.shackspacehosting.engineering.openshiftpvmanager.storage.providers;

import com.openshift.restclient.model.volume.property.IPersistentVolumeProperties;
import com.openshift.restclient.utils.MemoryUnit;

import java.util.Map;
import java.util.UUID;

public interface IStorageManagementProvider {
	public IPersistentVolumeProperties createPersistentVolume(Map<String, String> annotations, UUID uuid, long sizeInUnits, MemoryUnit unitSize) throws Exception;
}