package hub;

import devices.Device;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DeviceRegistry {
    private final Map<String, Device> devices = new ConcurrentHashMap<>();

    public void register(Device device) {
        devices.put(device.getName(), device);
    }

    public void deregister(String deviceName) {
        devices.remove(deviceName);
    }

    public Optional<Device> get(String name) {
        return Optional.ofNullable(devices.get(name));
    }

    public Collection<Device> getAll() {
        return devices.values();
    }
}