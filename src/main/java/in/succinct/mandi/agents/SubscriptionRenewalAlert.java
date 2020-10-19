package in.succinct.mandi.agents;

import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.agent.AgentSeederTask;
import com.venky.swf.plugins.background.core.agent.AgentSeederTaskBuilder;

import java.util.List;

public class SubscriptionRenewalAlert implements AgentSeederTaskBuilder , Task{

    @Override
    public void execute() {

    }

    @Override
    public AgentSeederTask createSeederTask() {
        return new AgentSeederTask() {
            @Override
            public List<Task> getTasks() {
                return null;
            }

            @Override
            public String getAgentName() {
                return null;
            }
        };
    }

    public static final String SUBSCRIPTION_RENEWAL_ALERT
}
