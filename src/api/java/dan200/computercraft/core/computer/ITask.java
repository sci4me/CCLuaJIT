package dan200.computercraft.core.computer;

public interface ITask {
    Computer getOwner();

    void execute();
}