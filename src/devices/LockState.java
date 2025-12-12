package devices;

public class LockState implements DeviceState {
    private final boolean locked;

    public LockState(boolean locked) {
        this.locked = locked;
    }

    public boolean isLocked() { return locked; }

    @Override
    public String toString() {
        return "LockState{locked=" + locked + "}";
    }
}