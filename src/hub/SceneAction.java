package hub;

import devices.Device;
import devices.DeviceState;
import exceptions.ValidationException;

public class SceneAction {
    private final Device device;
    private final DeviceState targetState;

    // hold a field of type Device directly
    public SceneAction(Device device, DeviceState targetState) {
        this.device = device;
        this.targetState = targetState;
    }

    public Device getDevice() { return device; }

    public void execute() throws ValidationException {
        device.applyState(targetState);
    }
}