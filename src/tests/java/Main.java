public class Main {
    public static void main(String[] args){
        String[] values ="8707 50 [Except 8703 70 ]".split("(<br[ /]*>)|(,)");
        for (String v : values){
            System.out.println(v.replaceAll("(\\[Except.*])",""));
        }
    }
}
