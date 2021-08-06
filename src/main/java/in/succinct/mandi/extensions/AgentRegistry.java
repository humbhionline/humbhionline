package in.succinct.mandi.extensions;

import com.venky.swf.plugins.background.core.agent.Agent;
import in.succinct.mandi.agents.SubscriptionRenewalAlert;
import in.succinct.mandi.agents.beckn.RegisterProviderLocationAgent;
import in.succinct.mandi.agents.beckn.RegisterProviderLocationAgent.RegisterProviderLocationTask;

public class AgentRegistry {
    static {
        Agent.instance().registerAgentSeederTaskBuilder(SubscriptionRenewalAlert.SUBSCRIPTION_RENEWAL_ALERT,new SubscriptionRenewalAlert());
        Agent.instance().registerAgentSeederTaskBuilder(RegisterProviderLocationAgent.REGISTER_PROVIDER_LOCATION,new RegisterProviderLocationAgent());
    }
}
