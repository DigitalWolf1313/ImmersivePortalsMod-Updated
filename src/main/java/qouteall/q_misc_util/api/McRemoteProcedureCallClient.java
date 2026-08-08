package qouteall.q_misc_util.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

import static qouteall.q_misc_util.api.McRemoteProcedureCall.createPacketToSendToServer;

@Environment(EnvType.CLIENT)
public class McRemoteProcedureCallClient {
    public static void tellServerToInvoke(
            String methodPath, Object... arguments
    ) {
        var packet = createPacketToSendToServer(methodPath, arguments);
        Minecraft.getInstance().getConnection().send(packet);
    }
}
