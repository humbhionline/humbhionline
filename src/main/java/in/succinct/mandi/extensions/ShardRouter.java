package in.succinct.mandi.extensions;

import com.venky.extension.Extension;
import com.venky.extension.Registry;

import java.util.ArrayList;
import java.util.List;

public class ShardRouter implements Extension {
    static {
        Registry.instance().registerExtension("swf.before.routing",new ShardRouter());
    }
    @Override
    public void invoke(Object... context) {
        List<String> pathElements = (List<String>)context[0];
        if (pathElements == null || pathElements.isEmpty()){
            return;
        }
        String firstPathElement = pathElements.get(0);
        if (!firstPathElement.equalsIgnoreCase("network")){
            return;
        }
        List<String> newPathElements = new ArrayList<>();
        newPathElements.add(pathElements.get(0));
        if (pathElements.size() >= 2){
            newPathElements.add(pathElements.get(1));
        }
        StringBuilder path = new StringBuilder();
        for (int i = 2 ; i < pathElements.size() ;i  ++ ){
            path.append("/");
            path.append(pathElements.get(i));
        }
        if (path.length() > 0){
            newPathElements.add(path.toString());
        }
        pathElements.clear();
        pathElements.addAll(newPathElements);

    }
}
