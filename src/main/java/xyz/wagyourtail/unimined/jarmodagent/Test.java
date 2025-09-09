package xyz.wagyourtail.unimined.jarmodagent;

public class Test {

    public class Inner {

    }

    public static void main(String[] args) {
        Test t = null;
        t.new Inner() {};
    }
}
