package in.succinct.mandi.agents;

import com.venky.core.io.ByteArrayInputStream;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.Model;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.agent.AgentSeederTask;
import com.venky.swf.plugins.background.core.agent.AgentSeederTaskBuilder;
import com.venky.swf.plugins.datamart.agent.datamart.ExtractorTask;
import com.venky.swf.plugins.datamart.db.model.EtlRestart;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public abstract class ResolutionReducer<M extends Model> extends ExtractorTask<M> implements AgentSeederTaskBuilder {
    @Override
    protected Task createTransformTask(M model) {
        return new ResizeTask(model);
    }

    public void resize(M model){
        new ResizeTask(model).resize();
    }

    @Override
    public AgentSeederTask createSeederTask() {
        return this;
    }


    public static final int MAX_SIZE = 2000000;
    @Override
    protected Expression getWhereClause(EtlRestart restart) {
        Expression expression =  super.getWhereClause(restart).
                add(new Expression(getReflector().getPool(),getImageFieldName()+"_CONTENT_SIZE", Operator.GT, MAX_SIZE)).
                add(new Expression(getReflector().getPool(),getImageFieldName()+"_CONTENT_TYPE", Operator.LK, "image/%"));

        return expression;
    }
    protected String getImageFieldName(){
        return "IMAGE";
    }


    public class ResizeTask implements  Task{
        M m;
        public ResizeTask(M  m){
            this.m = m;
        }

        @Override
        public void execute() {
            resize();
            m.save();
        }

        public void resize(){
            try {
                if (m == null){
                    return;
                }
                int contentSize = m.getReflector().getContentSize(m,getImageFieldName());
                InputStream imageInputStream = m.getReflector().get(m,getImageFieldName());

                if (contentSize > MAX_SIZE) {
                    BufferedImage image = ImageIO.read(imageInputStream);
                    int height = image.getHeight();
                    int width = image.getWidth();
                    double scale = Math.sqrt(1.0*MAX_SIZE/contentSize);
                    if (height < width && width > 640.0){
                        scale = Math.min(640.0 / width,scale) ;
                    }else if (height > 640 ){
                        scale = Math.min(640.0 / height,scale);
                    }

                    BufferedImage resized = resize(image,scale);
                    ByteArrayOutputStream byteArrayOutputStream= new ByteArrayOutputStream();

                    m.getReflector().set(m,getImageFieldName()+"_CONTENT_TYPE",MimeType.IMAGE_PNG.toString());


                    String fileName = m.getReflector().get(m,getImageFieldName()+"_CONTENT_NAME");
                    if (ObjectUtil.isVoid(fileName)){
                        fileName = String.valueOf(m.getId());
                    }else {
                        int index = fileName.lastIndexOf('.');
                        if (index >= 0){
                            fileName = fileName.substring(0,index)+".png";
                        }else {
                            fileName = fileName+".png";
                        }
                    }

                    m.getReflector().set(m,getImageFieldName()+"_CONTENT_NAME",fileName);

                    ImageIO.write(resized,"png",byteArrayOutputStream);
                    m.getReflector().set(m,getImageFieldName(),new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));

                    m.getReflector().set(m,getImageFieldName()+"_CONTENT_SIZE", byteArrayOutputStream.size());
                }
            }catch (Exception ex){
                throw new RuntimeException(ex);
            }

        }
        public BufferedImage resize(BufferedImage img, int height, int width) {
            Image tmp = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = resized.createGraphics();
            g2d.drawImage(tmp, 0, 0, null);
            g2d.dispose();
            return resized;
        }
        public BufferedImage resize(BufferedImage inputImage, double percent) {
            int scaledWidth = (int) (inputImage.getWidth() * percent);
            int scaledHeight = (int) (inputImage.getHeight() * percent);
            return resize(inputImage, scaledHeight,scaledWidth);
        }
    }
}
