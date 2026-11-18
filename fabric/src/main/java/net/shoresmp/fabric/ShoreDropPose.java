package net.shoresmp.fabric;

/** Entity extraction and drawing can run at different times. Keep pose data on each render state. */
public interface ShoreDropPose {
    void shore$setPose(boolean grounded, boolean block, float age, float heading);
    boolean shore$grounded();
    boolean shore$block();
    float shore$age();
    float shore$heading();
}
