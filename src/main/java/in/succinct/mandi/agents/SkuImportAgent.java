package in.succinct.mandi.agents;

import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.agent.AgentSeederTask;
import com.venky.swf.plugins.background.core.agent.AgentSeederTaskBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class SkuImportAgent implements Task, AgentSeederTaskBuilder {
    public SkuImportAgent(){

    }
    String sku ;
    public SkuImportAgent(String sku){
        this.sku = sku;
    }
    @Override
    public void execute() {
        sku.split("^(.*)([0-9|' ']*)(G|GM|GMS|)$");

    }

    @Override
    public AgentSeederTask createSeederTask() {
        return new AgentSeederTask() {
            @Override
            public List<Task> getTasks() {
                List<Task> tasks = new ArrayList<>();
                try {
                    File items = new File("./doc/items");
                    if (items.exists()) {
                        BufferedReader is = new BufferedReader(new FileReader(items));
                        while (is.ready()) {
                            is.readLine();
                        }
                    }
                }catch (Exception ex){

                }
                return tasks;
            }

            @Override
            public String getAgentName() {
                return SKU_IMPORT;
            }
        };
    }

    public static final String SKU_IMPORT = "SKU_IMPORT";
}
