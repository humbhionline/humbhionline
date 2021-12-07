package in.succinct.mandi.agents.beckn;

import com.venky.geo.GeoCoordinate;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.agent.AgentSeederTask;
import com.venky.swf.plugins.background.core.agent.AgentSeederTaskBuilder;
import com.venky.swf.plugins.datamart.agent.datamart.ExtractorTask;
import in.succinct.beckn.Circle;
import in.succinct.beckn.City;
import in.succinct.beckn.Country;
import in.succinct.beckn.Location;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import org.json.simple.JSONObject;

public class RegisterProviderLocationAgent extends ExtractorTask<Facility> implements AgentSeederTaskBuilder {
    @Override
    protected Task createTransformTask(Facility facility) {
        return new RegisterProviderLocationTask(facility);
    }

    @Override
    public String getAgentName() {
        return REGISTER_PROVIDER_LOCATION;
    }

    public static final String REGISTER_PROVIDER_LOCATION = "REGISTER_PROVIDER_LOCATION";
    @Override
    public AgentSeederTask createSeederTask() {
        return this;
    }

    public static class RegisterProviderLocationTask implements Task {
        Facility facility;
        public RegisterProviderLocationTask(Facility facility){
            this.facility = facility;
        }

        @Override
        public void execute() {

            Location provider_location = new Location();
            provider_location.setId(BecknUtil.getBecknId(facility.getId(), Entity.provider_location));

            Circle circle = new Circle();
            City city = new City();
            Country country = new Country();
            city.setCode(facility.getCity().getCode());
            city.setName(facility.getCity().getName());
            country.setName(facility.getCountry().getName());
            country.setCode(facility.getCountry().getIsoCode());
            provider_location.setCity(city);
            provider_location.setCountry(country);
            provider_location.setGps(new GeoCoordinate(facility));
            provider_location.setCircle(circle);
            circle.setGps(provider_location.getGps());
            if (facility.isDeliveryProvided()){
                circle.setRadius(facility.getDeliveryRadius());
            }else {
                circle.setRadius(0.0D);
            }

            String apiName = "/register_location";
            if (!facility.isPublished()){
                apiName = "/deregister_location";
            }

            Call<JSONObject> call = new Call<JSONObject>().url(BecknUtil.getRegistryUrl() + apiName).method(HttpMethod.POST).
                    input(provider_location.getInner()).
                    inputFormat(InputFormat.JSON).
                    header("Content-Type", MimeType.APPLICATION_JSON.toString());

            call.header("Authorization", new Request(provider_location.toString()).generateAuthorizationHeader(BecknUtil.getNetworkParticipantId(),
                    BecknUtil.getCryptoKeyId()));
            call.getResponseAsJson();

        }
    }

}
