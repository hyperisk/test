package hyperden.mesh_entity_paint;

import hyperden.heli_one.renderer.Camera;
import hyperden.util.ColorScheme;
import hyperden.util.MathUtil;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;

public class PaintPathSubentity {
    private PaintPathSubmesh submesh_;
    private Path transformedElemPath_;
    public float cameraEffectAlphaMul_;
    public float effectAlphaMul_;
    public float effectRedMul_;
    public float effectGreenMul_;
    public float effectBlueMul_;
    public boolean effectHidden_;
    public boolean effectExcludeFromTornApart_;
    private Matrix animTransform_;
    static final float[] horzMirrorValues_ = {
            -1.f, 0.f, 0.f,
            0.f, 1.f, 0.f,
            0.f, 0.f, 1.f
    };
    static final float[] vertMirrorValues_ = {
            1.f, 0.f, 0.f,
            0.f, -1.f, 0.f,
            0.f, 0.f, 1.f
    };
    static private Matrix mirrorHorzTransform_ = new Matrix();
    static private Matrix mirrorVertTransform_ = new Matrix();
    private Matrix frameUpdateTransform_;
    public boolean applyEffectMirrowHorz_;
    public int effectTornApartPercent_;
    public int effectGroundUnitUpsideDownPercent_;

    // computed when blow up effect starts
    private float tornApartRelPosX_;
    private float tornApartRelPosY_;
    private int tornApartRot_;
    private Matrix effectTransform_;

    public PaintPathSubentity(PaintPathSubmesh submesh) {
        submesh_ = submesh;
        transformedElemPath_ = new Path();
        animTransform_ = new Matrix();
        cameraEffectAlphaMul_ = 1.f;
        mirrorHorzTransform_.setValues(horzMirrorValues_);
        mirrorVertTransform_.setValues(vertMirrorValues_);
        resetEffectColorMul();
        frameUpdateTransform_ = new Matrix();
        applyEffectMirrowHorz_ = false;
        effectTornApartPercent_ = 0;
        effectGroundUnitUpsideDownPercent_ = 0;
        effectTransform_ = null;
        effectHidden_ = submesh_.hidden_;
        effectExcludeFromTornApart_ = submesh.excludeFromTornApart_;
    }

    public boolean draw(Canvas canvas, Paint paint, Camera camera, Matrix entityCameraTransform) {
    	if ((submesh_.aabb_ != null) && !submesh_.aabb_.isEmpty()) {
    		int submeshAabbCartSize = submesh_.aabb_.height() + submesh_.aabb_.width();
    		if (!submesh_.excludeFromLOD_ && submeshAabbCartSize < camera.getMinAabbCartSizeToDrawLOD()) {
    			return false;
    		}
    	}
        frameUpdateTransform_.reset();
        if (applyEffectMirrowHorz_) {
            frameUpdateTransform_.postConcat(mirrorHorzTransform_);
        }
        if (effectTornApartPercent_ >= 99) {
            effectTransform_ = null;
        } else if (effectGroundUnitUpsideDownPercent_ >= 99) {
            effectTransform_ = null;
        } else if (effectTornApartPercent_ > 0) {
            frameUpdateTransform_.postConcat(getTornApartEffect());
        } else if (effectGroundUnitUpsideDownPercent_ > 0) {
            frameUpdateTransform_.postConcat(getGroundUnitUpsideDownEffect());
        } else if (!animTransform_.isIdentity()) {
            frameUpdateTransform_.postConcat(animTransform_);
        }
        frameUpdateTransform_.postConcat(entityCameraTransform);

        transformedElemPath_.rewind();
        submesh_.originalElemPath_.transform(frameUpdateTransform_, transformedElemPath_);

        int alpha = Color.alpha(submesh_.color_);
        int red = Color.red(submesh_.color_);
        int green = Color.green(submesh_.color_);
        int blue = Color.blue(submesh_.color_);

        if (cameraEffectAlphaMul_ != 1.f) {
            alpha = (int) ((float) alpha * cameraEffectAlphaMul_);
        }
        if (effectAlphaMul_ != 1.f) {
        	alpha = (int) ((float) alpha * effectAlphaMul_);
        }
        if (effectTornApartPercent_ > 0) {
            alpha = (int) ((float) alpha * (float) (100 - effectTornApartPercent_) / 100.f);
        } else if (effectGroundUnitUpsideDownPercent_ > 50) {
            alpha = (int) ((float) alpha
                    * (float) (100 - effectGroundUnitUpsideDownPercent_) / 100.f);
        }
        if (effectRedMul_ != 1.f) {
            red = (int) ((float) red * effectRedMul_);
        }
        if (effectGreenMul_ != 1.f) {
            green = (int) ((float) green * effectGreenMul_);
        }
        if (effectBlueMul_ != 1.f) {
            blue = (int) ((float) blue * effectBlueMul_);
        }
        paint.setColor(ColorScheme.getBrightnessAdjustedColor(alpha, red, green, blue, true));
        paint.setStyle(submesh_.paintStyle_);
        paint.setStrokeWidth(submesh_.strokeWidth_);
        canvas.drawPath(transformedElemPath_, paint);
        
        return true;
    }

    public boolean hasAnimKey(String animKeyName) {
        return submesh_.hasAnimTransform(animKeyName);
    }

    public void applyAnimKeyFromSubmesh(String animKeyName, int percent, boolean resetPrevAnim) {
        if (!submesh_.hasAnimTransform(animKeyName)) {
            throw new RuntimeException("anim key " + animKeyName + " does not exist");
        }
        if (resetPrevAnim) {
        	animTransform_.set(submesh_.getAnimKey(animKeyName).getMatrix(percent, applyEffectMirrowHorz_));
        } else {
            animTransform_.postConcat(submesh_.getAnimKey(animKeyName).getMatrix(percent, applyEffectMirrowHorz_));
        }
    }

    public void applyAnimFromParentSubentity(Matrix parentTransform, boolean resetPrevAnim) {
        if (resetPrevAnim) {
            animTransform_.reset();
        }
        animTransform_.postConcat(parentTransform);
    }

    public void resetAnimTransform() {
        animTransform_.reset();
    }

    public final Matrix getAnimTransform() {
        return animTransform_;
    }

    public void applyEffectColorMul(int percent, String param) {
    	float norm = (float)percent / 100.f;
    	norm = Math.min(1.f, norm);
    	norm = Math.max(0, norm);
    	float normInv = 1.f - norm;
        effectAlphaMul_ = 1.f * normInv + PaintPathMesh.getFloatFromParamString(param, 0) * norm;
        effectRedMul_ = 1.f * normInv + PaintPathMesh.getFloatFromParamString(param, 1) * norm;
        effectGreenMul_ = 1.f * normInv + PaintPathMesh.getFloatFromParamString(param, 2) * norm;
        effectBlueMul_ = 1.f * normInv + PaintPathMesh.getFloatFromParamString(param, 3) * norm;
    }

    public void resetEffectColorMul() {
        effectAlphaMul_ = 1.f;
        effectRedMul_ = 1.f;
        effectGreenMul_ = 1.f;
        effectBlueMul_ = 1.f;
    }

    private Matrix getTornApartEffect() {
        if (effectTransform_ == null) {
            effectTransform_ = new Matrix();
            if (submesh_.aabb_.isEmpty()) {
                return effectTransform_;
            }
            tornApartRelPosX_ = submesh_.aabb_.centerX() * MathUtil.getRandomFloat(1.5f, 3.f); 
            tornApartRelPosY_ = submesh_.aabb_.centerY() * MathUtil.getRandomFloat(1.5f, 3.f);
            if (applyEffectMirrowHorz_) {
                tornApartRelPosX_ = -1 * tornApartRelPosX_;
            }
            tornApartRot_ = MathUtil.getRandomInt(-30, 30);
        }

        if ((tornApartRelPosX_ < 1) && (tornApartRelPosY_ < 1)) {
            return effectTransform_;
        }

        float relPosX = tornApartRelPosX_ * (float) effectTornApartPercent_ / 100.f;
        float relPosY = tornApartRelPosY_ * (float) effectTornApartPercent_ / 100.f;
        float rotDeg = tornApartRot_ * (float) effectTornApartPercent_ / 100.f;
        float scale = (float) (100 - effectTornApartPercent_ / 2) / 100.f;

        effectTransform_.reset();
        effectTransform_
                .postScale(scale, scale, tornApartRelPosX_, tornApartRelPosY_);
        effectTransform_.postRotate(rotDeg, tornApartRelPosX_, tornApartRelPosY_);
        effectTransform_.preTranslate(relPosX, relPosY);
        return effectTransform_;
    }

    private Matrix getGroundUnitUpsideDownEffect() {
        if (effectTransform_ == null) {
            effectTransform_ = new Matrix();
            if (submesh_.aabb_.isEmpty()) {
                return effectTransform_;
            }
        }

        effectTransform_.reset();
        float moveUpDist = submesh_.aabb_.height() / 2.f;
        if (effectGroundUnitUpsideDownPercent_ < 50) {
            effectTransform_.postScale(1.f,
                    1.f - effectGroundUnitUpsideDownPercent_ / 150.f);
            effectTransform_.postTranslate(0, moveUpDist
                    * effectGroundUnitUpsideDownPercent_ / 50);
        } else {
            effectTransform_.postConcat(mirrorVertTransform_);
            effectTransform_.postScale(1.f,
                    1.f - (effectGroundUnitUpsideDownPercent_ - 50) / 150.f);
            effectTransform_.postTranslate(0, moveUpDist
                    * (100 - effectGroundUnitUpsideDownPercent_) / 50);
        }
        return effectTransform_;
    }

    public void resetAllEffects() {
        applyEffectMirrowHorz_ = false;
        resetEffectColorMul();
        effectTornApartPercent_ = 0;
        effectHidden_ = submesh_.hidden_;
    }

    protected void logDescription(String description) {
        submesh_.logDescription(description);
    }
}
