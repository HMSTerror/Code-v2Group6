package devices;

public class LightState implements DeviceState {
    private final boolean power;
    private final int brightness;

    public LightState(boolean power, int brightness) {
        if (brightness < 0 || brightness > 100) {
            throw new IllegalArgumentException("Brightness must be 0-100");
        }
        this.power = power;
        this.brightness = brightness;
    }

    public boolean isPowerOn() { return power; }
    public int getBrightness() { return brightness; }

    @Override
    public String toString() {
        return "LightState{power=" + power + ", brightness=" + brightness + "}";
    }
}