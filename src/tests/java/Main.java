import com.venky.core.math.DoubleHolder;

public class Main {
    public static void main(String[] args){

        for (Double value : new Double[]{1.001, 1.01, 1.09, 1.1,1.12233,1.4,1.45,1.55, 1.5, 1.99 , 1.9 ,1.91 , 1.98}){
            if (value - Math.floor(value) < 0.1 || Math.ceil(value) - value <0.1){
                System.out.println(value + " rounded to " + (double)Math.round(value));
                continue;
            }
            System.out.println(value + " rounded to " + new DoubleHolder(value,2).getHeldDouble().doubleValue());
        }


    }

}
