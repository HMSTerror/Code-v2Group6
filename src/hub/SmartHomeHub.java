package hub;

import devices.Device;
import exceptions.ExecutionException;
import users.Permission;
import users.User;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SmartHomeHub {
    private final DeviceRegistry deviceRegistry = new DeviceRegistry();
    private final SceneExecutor sceneExecutor = new SceneExecutor();
    private final Map<String, Scene> scenes = new ConcurrentHashMap<>();

    public void registerDevice(User user, Device device) {
        if (!user.hasPermission(Permission.REGISTER_DEVICE)) throw new SecurityException("Access Denied");
        deviceRegistry.register(device);
    }

    public void deregisterDevice(User user, String deviceName) {
        if (!user.hasPermission(Permission.DEREGISTER_DEVICE)) {
            throw new SecurityException("Access Denied: User cannot deregister devices.");
        }
        deviceRegistry.deregister(deviceName);
    }

    public Collection<Device> getAllDevices() {
        return deviceRegistry.getAll();
    }

    public Device getDevice(String name) {
        return deviceRegistry.get(name).orElse(null);
    }

    public void createScene(User user, Scene scene) {
        if (!user.hasPermission(Permission.EDIT_SCENES)) throw new SecurityException("Access Denied");
        scenes.put(scene.getName(), scene);
    }

    public void executeScene(User user, String sceneName) throws ExecutionException {
        Scene scene = scenes.get(sceneName);
        if (scene == null) throw new ExecutionException("Scene not found: " + sceneName);
        sceneExecutor.execute(scene, user);
    }
}