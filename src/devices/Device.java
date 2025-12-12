package devices;

import exceptions.ValidationException;

public abstract class Device {
    protected final String name;
    protected final String type;
    protected DeviceState state;

    public Device(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public DeviceState getState() { return state; }

    public abstract void applyState(DeviceState targetState) throws ValidationException;

    public void restoreState(DeviceState original) {
        // restore to original state
        this.state = original;
    }

    @Override
    public String toString() {
        return String.format("Device{name='%s', type=%s, state=%s}", name, type, state);
    }
}