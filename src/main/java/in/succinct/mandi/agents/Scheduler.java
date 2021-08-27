package in.succinct.mandi.agents;

import com.venky.swf.db.Database;
import com.venky.swf.db.Transaction;
import com.venky.swf.plugins.background.core.Task;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Scheduler {
    private static Scheduler instance = null;
    public static Scheduler instance(){
        if (instance == null){
            synchronized (Scheduler.class){
                if (instance == null){
                    instance =new Scheduler();
                }
            }
        }
        return instance;
    }
    private Scheduler(){

    }
    ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
    public void scheduleAfter(long delay, final Task task){
        service.schedule(() -> {
            Database db = null;
            Transaction txn = null;
            try {
                db = Database.getInstance(false);
                txn = db.getCurrentTransaction();
                task.execute();
                txn.commit();
            }catch (Exception ex){
                if (txn != null) {
                    txn.rollback(ex);
                }
            }finally {
                if (db != null){
                    db.close();
                }
            }
        },delay, TimeUnit.MILLISECONDS);
    }
    public void scheduleAt(long at, final Task task){
        long delay = Math.max(0,at - System.currentTimeMillis());
        scheduleAfter(delay,task);

    }
}
