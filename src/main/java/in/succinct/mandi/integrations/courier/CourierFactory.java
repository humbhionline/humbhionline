package in.succinct.mandi.integrations.courier;

import freemarker.ext.beans.SingletonCustomizer;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.integrations.courier.Courier.CourierOrder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CourierFactory {
    private static volatile CourierFactory instance = null;
    private static final Object mutex = new Object();

    public static CourierFactory getInstance(){
        if (instance == null){
            synchronized (mutex){
                if (instance == null){
                    instance = new CourierFactory();
                }
            }
        }
        return instance;
    }

    private Map<String,Courier> courierMap = new ConcurrentHashMap<String,Courier>(){{
       put(Inventory.WEFAST,new Wefast());
    }};

    public Courier getCourier(String name){
        Courier courier =  courierMap.get(name);
        if (null == courier){
            throw new RuntimeException(String.format("Don't know how to integrate with Courier (%s)!",name));
        }
        return courier;
    }
}
