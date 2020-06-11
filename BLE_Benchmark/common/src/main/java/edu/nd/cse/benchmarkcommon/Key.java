package edu.nd.cse.benchmarkcommon;

/*
From: https://stackoverflow.com/questions/14677993/how-to-create-a-hashmap-with-two-keys-key-pair-value
 */
public class Key {
    private final int x;
    private final String y;

    public Key(int x, String y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Key)) return false;
        Key key = (Key) o;
        return x == key.x && y.equals(key.y);
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y.hashCode();
        return result;
    }

}
