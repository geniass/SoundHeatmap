package geniass.soundmap;

/**
 * Created with IntelliJ IDEA.
 * User: ari
 * Date: 5/4/13
 * Time: 7:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class Decibels {
    double db;
    long num = 1;

    public Decibels(double db) {
        this.db = db;
    }

    public double getDb() {
        return db;
    }

    public void setDb(double db) {
        this.db = db;
    }

    public void addToAverage(double db) {
        this.db = this.db + ((db - this.db) / (num + 1));
    }
}
