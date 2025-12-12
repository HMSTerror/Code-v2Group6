package hub;

import devices.Device;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A simple device group which stores device objects directly.
 * Refactored to use Device objects instead of String names.
 */
public class DeviceGroup {
    private final String name;
    private final Set<Device> devices = new HashSet<>();

    public DeviceGroup(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public void addDevice(Device device) {
        devices.add(device);
    }

    public void removeDevice(Device device) {
        devices.remove(device);
    }

    public Set<Device> getDevices() {
        return Collections.unmodifiableSet(devices);
    }

    @Override
    public String toString() {
        return "DeviceGroup{" + name + ", size=" + devices.size() + "}";
    }
}