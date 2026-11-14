package net.shoresmp.fabric;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.shoresmp.core.Payments;
import java.util.ArrayList;
import java.util.List;

public final class PaymentScreen extends ShoreDialog {
    private static final int PER_PAGE=4;
    private final List<Payments.Payment> plan;
    private final ClientPlayNetworkHandler connection;
    private final long sessionEpoch=ShoreClient.sessionEpoch;
    private final long createdAt=System.nanoTime();
    private int page;
    public PaymentScreen(Screen parent,List<Payments.Payment> plan,ClientPlayNetworkHandler connection){
        super("Confirm payments",parent);this.plan=List.copyOf(plan);this.connection=connection;
    }
    private int pages(){return Math.max(1,(plan.size()+PER_PAGE-1)/PER_PAGE);}
    @Override protected float dialogScale(){return ShoreClient.config.paymentScale/100f;}
    @Override protected float preferredWidth(){return 420;}
    @Override protected float buttonMinimum(){return 44;}
    @Override protected String icon(){return "check";}
    @Override protected String subtitle(){return "Up to $"+Payments.format(Payments.total(plan))+" across "+plan.size()+" player"+(plan.size()==1?"":"s");}
    @Override protected List<Line> lines(){
        var out=new ArrayList<Line>();
        for(int i=page*PER_PAGE;i<Math.min(plan.size(),page*PER_PAGE+PER_PAGE);i++){
            var p=plan.get(i);out.add(new Line(p.user().name()+"   $"+Payments.format(p.amount()),TEXT,false));
        }
        while(out.size()<Math.min(PER_PAGE,plan.size()))out.add(new Line("",TEXT,false));
        out.add(new Line((pages()>1?"Page "+(page+1)+" of "+pages()+"  ·  ":"")+"One command every 2 seconds. No automatic retries.",MUTED,false));
        return out;
    }
    @Override protected List<Button> buttons(){
        var out=new ArrayList<Button>();
        if(pages()>1){
            out.add(new Button("<",SECONDARY,()->{if(page>0)page--;}));
            out.add(new Button(">",SECONDARY,()->{if(page+1<pages())page++;}));
        }
        out.add(new Button("Cancel",SECONDARY,this::close));
        out.add(new Button("Confirm",PRIMARY,()->{
            try{
                if(client.getNetworkHandler()!=connection||sessionEpoch!=ShoreClient.sessionEpoch)
                    throw new IllegalArgumentException("Server session changed; create a new payment plan.");
                if(System.nanoTime()-createdAt>60_000_000_000L)
                    throw new IllegalArgumentException("Payment preview expired; create a new payment plan.");
                ShoreClient.payments.start(plan);close();
            }catch(IllegalArgumentException e){ShoreClient.tellError(e.getMessage());close();}
        }));
        return out;
    }
}
