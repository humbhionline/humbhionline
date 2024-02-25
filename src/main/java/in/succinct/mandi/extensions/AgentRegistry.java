package in.succinct.mandi.extensions;

import com.venky.swf.plugins.background.core.agent.Agent;
import in.succinct.mandi.agents.SubscriptionRenewalAlert;

public class AgentRegistry {
    static {
        Agent.instance().registerAgentSeederTaskBuilder(SubscriptionRenewalAlert.SUBSCRIPTION_RENEWAL_ALERT,new SubscriptionRenewalAlert());
    }
}
