package cn.dancingsnow.neoecoae.network;

/** State is owned by a menu instance, never by a reused container id. */
public interface NetworkMenu {
    MenuDataSync neoecoae$dataSync();

    void neoecoae$receiveData(int kind, byte[] data);

    void neoecoae$resetData();
}
