package io.github.ooboomberoo.precaststructure.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.platform.Platform;
import io.github.ooboomberoo.precaststructure.block.entity.StructureScannerBlockEntity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3dc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Soft Sable / Create Aeronautics compat: Simulated contraptions live in sub-level plot
 * storage coordinates but render through a {@code Pose3d}. Scan holograms must apply that
 * pose or they draw at the invisible plot location.
 *
 * <p>Transform matches Sable's {@code SubLevelRenderData.getTransformation}:
 * {@code T(position - camera) * R * S}. Plot positions are converted to pose-local space as
 * {@code plotPos - rotationPoint} in double precision before entering the float PoseStack —
 * baking absolute plot coords (~2e7) into the matrix destroys GPU precision and makes overlays
 * vanish.
 */
public final class SableCompatClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(SableCompatClient.class);

    private static final Object HELPER;
    private static final Method GET_CONTAINING_CLIENT;
    private static final Method RENDER_POSE;
    private static final Method POSE_POSITION;
    private static final Method POSE_ORIENTATION;
    private static final Method POSE_SCALE;
    private static final Method POSE_ROTATION_POINT;

    static {
        Object helper = null;
        Method getContainingClient = null;
        Method renderPose = null;
        Method posePosition = null;
        Method poseOrientation = null;
        Method poseScale = null;
        Method poseRotationPoint = null;
        boolean sableLoaded = false;
        try {
            sableLoaded = Platform.isModLoaded("sable");
        } catch (Throwable ignored) {
            sableLoaded = false;
        }
        if (sableLoaded) {
            try {
                Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable");
                Field helperField = sable.getField("HELPER");
                helper = helperField.get(null);
                getContainingClient = helper.getClass().getMethod("getContainingClient", BlockEntity.class);
                Class<?> clientSubLevel = Class.forName("dev.ryanhcode.sable.sublevel.ClientSubLevel");
                renderPose = clientSubLevel.getMethod("renderPose", float.class);
                Class<?> pose3dc = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
                posePosition = pose3dc.getMethod("position");
                poseOrientation = pose3dc.getMethod("orientation");
                poseScale = pose3dc.getMethod("scale");
                poseRotationPoint = pose3dc.getMethod("rotationPoint");
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.warn(
                    "Sable sub-level pose API could not be bound; scan holograms may be invisible on Simulated contraptions",
                    e
                );
                helper = null;
                getContainingClient = null;
                renderPose = null;
                posePosition = null;
                poseOrientation = null;
                poseScale = null;
                poseRotationPoint = null;
            }
        }
        HELPER = helper;
        GET_CONTAINING_CLIENT = getContainingClient;
        RENDER_POSE = renderPose;
        POSE_POSITION = posePosition;
        POSE_ORIENTATION = poseOrientation;
        POSE_SCALE = poseScale;
        POSE_ROTATION_POINT = poseRotationPoint;
    }

    private SableCompatClient() {
    }

    public static boolean isReady() {
        return HELPER != null
            && GET_CONTAINING_CLIENT != null
            && RENDER_POSE != null
            && POSE_POSITION != null
            && POSE_ORIENTATION != null
            && POSE_SCALE != null
            && POSE_ROTATION_POINT != null;
    }

    /**
     * Origin subtracted from plot-storage coordinates before they enter the float PoseStack.
     * Equals the pose rotation point when the scanner is in a sub-level; otherwise zero.
     */
    public static Vec3 plotOrigin(StructureScannerBlockEntity scanner, float partialTick) {
        if (!isReady()) {
            return Vec3.ZERO;
        }
        try {
            Object clientSubLevel = GET_CONTAINING_CLIENT.invoke(HELPER, scanner);
            if (clientSubLevel == null) {
                return Vec3.ZERO;
            }
            Object pose = RENDER_POSE.invoke(clientSubLevel, partialTick);
            if (pose == null) {
                return Vec3.ZERO;
            }
            Vector3dc rotationPoint = (Vector3dc) POSE_ROTATION_POINT.invoke(pose);
            return new Vec3(rotationPoint.x(), rotationPoint.y(), rotationPoint.z());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Vec3.ZERO;
        }
    }

    /**
     * Applies camera-relative translation, and when the scanner sits in a Sable sub-level,
     * the contraption pose {@code T(position - camera) * R * S} (no rotation-point bake).
     * Pair with {@link #plotOrigin} when converting plot-storage coordinates.
     * Caller should {@link PoseStack#pushPose()} / {@code popPose()} around this.
     */
    public static void applyCameraAndSubLevelTransform(
        PoseStack poseStack,
        StructureScannerBlockEntity scanner,
        Vec3 cameraPosition,
        float partialTick
    ) {
        if (!isReady()) {
            poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
            return;
        }
        try {
            Object clientSubLevel = GET_CONTAINING_CLIENT.invoke(HELPER, scanner);
            if (clientSubLevel == null) {
                poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
                return;
            }
            Object pose = RENDER_POSE.invoke(clientSubLevel, partialTick);
            if (pose == null) {
                poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
                return;
            }
            Vector3dc position = (Vector3dc) POSE_POSITION.invoke(pose);
            Quaterniondc orientation = (Quaterniondc) POSE_ORIENTATION.invoke(pose);
            Vector3dc scale = (Vector3dc) POSE_SCALE.invoke(pose);

            // Matches Sable SubLevelRenderData.getTransformation — keep large plot coords out.
            poseStack.translate(
                position.x() - cameraPosition.x,
                position.y() - cameraPosition.y,
                position.z() - cameraPosition.z
            );
            poseStack.mulPose(new Quaternionf(orientation));
            poseStack.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("Failed to apply Sable sub-level transform for scan hologram", e);
            poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        }
    }
}
