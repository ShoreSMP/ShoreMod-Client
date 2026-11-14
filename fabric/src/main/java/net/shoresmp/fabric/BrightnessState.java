package net.shoresmp.fabric;

/** Options serialization must see the unmodified setting, even while fullbright is enabled. */
public final class BrightnessState {
    private static final ThreadLocal<Integer> SAVING=ThreadLocal.withInitial(()->0);
    private BrightnessState(){}
    public static boolean saving(){return SAVING.get()>0;}
    public static void withoutOverride(Runnable save){
        int depth=SAVING.get();SAVING.set(depth+1);
        try{save.run();}finally{if(depth==0)SAVING.remove();else SAVING.set(depth);}
    }
}
