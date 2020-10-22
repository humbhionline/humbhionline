package in.succinct.mandi.agents;

import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.agent.AgentSeederTask;
import com.venky.swf.plugins.background.core.agent.AgentSeederTaskBuilder;
import com.venky.swf.plugins.templates.util.templates.TemplateEngine;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.mandi.db.model.User;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SubscriptionRenewalAlert implements AgentSeederTaskBuilder , Task{
    long userId = -1;
    public SubscriptionRenewalAlert(User user){
        this(user.getId());
    }
    public SubscriptionRenewalAlert(long userId){
        this.userId = userId;
    }
    public SubscriptionRenewalAlert(){

    }


    @Override
    public void execute() {
        User user = Database.getTable(User.class).get(userId);
        if (user == null){
            return;
        }
        if (!user.isNotificationEnabled()){
            return;
        }
        if (user.isBalanceBelowThresholdAlertSent()){
            return;
        }
        String templateName = "RenewHBO.ftlh";

        Map<String,Object> entityMap = TemplateEngine.getInstance().createEntityMap(Arrays.asList(user));
        TemplateEngine.getInstance().send(user,"Low Balance alert!" , "RenewHBO.ftlh",entityMap, getIncludedModelFields(),new HashMap<>());

        user.setBalanceBelowThresholdAlertSent(true);
        user.save();

    }

    protected Map<Class<? extends Model>, List<String>> getIncludedModelFields() {
        Map<Class<? extends Model>, List<String>> map = new HashMap<>();
        map.put(User.class, Arrays.asList("ID", "NAME", "LONG_NAME", "BALANCE_ORDER_LINE_COUNT"));
        return map;
    }

    @Override
    public AgentSeederTask createSeederTask() {
        return new AgentSeederTask() {
            @Override
            public List<Task> getTasks() {
                ModelReflector<User> userRef = ModelReflector.instance(User.class);
                Expression where = new Expression(userRef.getPool(), Conjunction.AND);
                where.add(new Expression(userRef.getPool(),"BALANCE_ORDER_LINE_COUNT", Operator.GT , 0));
                where.add(new Expression(userRef.getPool(),"BALANCE_ORDER_LINE_COUNT" , Operator.LT,  10));
                where.add(new Expression(userRef.getPool(),"BALANCE_BELOW_THRESHOLD_ALERT_SENT" , Operator.EQ,  false));

                List<Task> tasks = new Select("ID").from(User.class).where(where).execute(User.class).stream().map(u -> new SubscriptionRenewalAlert(u.getId())).collect(Collectors.toList());
                tasks.add(getFinishUpTask());

                return tasks;
            }

            @Override
            public String getAgentName() {
                return SUBSCRIPTION_RENEWAL_ALERT;
            }
        };
    }

    public static final String SUBSCRIPTION_RENEWAL_ALERT = "SUBSCRIPTION_RENEWAL_ALERT";
}
