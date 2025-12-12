package devices;

import exceptions.ValidationException;

/**
 * SmartLock:
 *  - locked: Boolean
 */
public class SmartLock extends Device {
    public SmartLock(String name) {
        super(name, "LOCK");
        this.state = new LockState(true);
    }

    @Override
    public void applyState(DeviceState targetState) throws ValidationException {
        if (targetState instanceof LockState ls) {
            this.state = ls;
            System.out.println("-> [Lock] " + name + " set to " + ls);
        } else {
            throw new ValidationException("Invalid state type for SmartLock");
        }
    }
}
