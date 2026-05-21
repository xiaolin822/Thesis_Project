package dk.ku.di.dms.vms.modb.definition.key;

/**
 * An interface for keys of rows and indexes
 */
public interface IKey {

    int hashCode();
    default String toString2() {
        return "#" + this.toString();
    }
}
