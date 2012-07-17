package hyperden.mesh_entity_paint;

import hyperden.heli_one.Config;
import hyperden.heli_one.gamestate.UnitStateBase;
import hyperden.heli_one.renderer.Camera;
import hyperden.util.Logger;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

public class PaintPathEntity {
	private PaintPathMesh mesh_;
	private float posX_;
	private float posY_;
	private float rotationDeg_;
	private boolean useRotation_;
	private float scaleX_;
	private float scaleY_;
	private boolean useScale_;
	public float cameraEffectAlphaMul_;
	public int aabbColor_;
	public int aabbLineWidth_;
	public static final int DEFAULT_AABB_COLOR = Color.rgb(100, 100, 100);
	public static int numSubentitiesDrawCalls_;
	public static int numSubentitiesDrawFilteredLOD_;
	
	private class SubentityNameDataMap extends TreeMap<String, PaintPathSubentity> {
		private static final long serialVersionUID = 1L;
	}
	private SubentityNameDataMap subentityNameDataMap_;
	boolean subentityNameDataMapAccessLock_;
	private Rect aabb_;
	private Path aabbPath_;
	public RectF aabbScreenCoord_;
	
	public PaintPathEntity(PaintPathMesh mesh) {
		mesh_ = mesh;
		subentityNameDataMap_ = new SubentityNameDataMap();
		aabb_ = new Rect();
		aabbScreenCoord_ = new RectF(); 
		useRotation_ = false;
		useScale_ = false;
		cameraEffectAlphaMul_ = 1.f;
		aabbColor_ = DEFAULT_AABB_COLOR;
		aabbLineWidth_ = 1;
		posX_ = UnitStateBase.INVALID_FLOAT_POS;
		
		subentityNameDataMapAccessLock_ = false;
		populateAllSubentities();
	}
	
	public void populateAllSubentities() {
	    while (subentityNameDataMapAccessLock_) {
	        // do nothing
	    }
	    subentityNameDataMapAccessLock_ = true;
	    
		subentityNameDataMap_.clear();
		Set<String> submeshNames = mesh_.getAllSubmeshNames();
		Iterator<String> submeshNameIter = submeshNames.iterator();
		while (submeshNameIter.hasNext()) {
			String submeshName = submeshNameIter.next();
			PaintPathSubmesh submesh = mesh_.getSubmesh(submeshName);
			PaintPathSubentity newSubentity = new PaintPathSubentity(submesh); 
			subentityNameDataMap_.put(submeshName, newSubentity);
		}
		subentityNameDataMapAccessLock_ = false;
	}
	
	public void setPosition(float posX, float posY) {
		posX_ = posX;
		posY_ = posY;
		aabb_.setEmpty();
		aabbScreenCoord_.setEmpty();
	}
	
	public boolean isPositionSet() {
		return (posX_ != UnitStateBase.INVALID_FLOAT_POS);
	}
	
	public void setPitchDown(float pitchDownDeg) {
		rotationDeg_ = pitchDownDeg;
		if (pitchDownDeg != 0.f) {
			useRotation_ = true;
		} else {
			useRotation_ = false;
		}
	}
	
	public void setOrientation(float oriDeg) {
	    rotationDeg_ = -1 * oriDeg;
	    if (oriDeg != 0.f) {
	        useRotation_ = true;
	    } else {
	        useRotation_ = false;
	    }
	}
	
	public void setScale(float scaleX, float scaleY) {
		scaleX_ = scaleX;
		scaleY_ = scaleY;
		if ((scaleX != 0.f) || (scaleY != 0.f)) {
			useScale_ = true;
			aabb_.setEmpty();
			aabbScreenCoord_.setEmpty();
		} else {
			useScale_ = false;
		}
	}
	
	public void draw(Canvas canvas, Paint paint, Camera camera) {
		if (!isPositionSet()) {
			return;
		}
        while (subentityNameDataMapAccessLock_) {
            // do nothing
        }
        subentityNameDataMapAccessLock_ = true;
	    
		Matrix entityCameraTransform;
		if (useScale_ && useRotation_) {
			entityCameraTransform = camera.getViewportTransform(rotationDeg_, posX_, posY_,
					scaleX_, scaleY_);
		} else if (useRotation_) {
			entityCameraTransform = camera.getViewportTransform(rotationDeg_, posX_, posY_);
		} else if (useScale_) {
			entityCameraTransform = camera.getViewportTransform(posX_, posY_, scaleX_, scaleY_);
		} else {
			entityCameraTransform = camera.getViewportTransform(posX_, posY_);
		}
		
		Set<String> subentityNameSet = subentityNameDataMap_.keySet();
		Iterator<String> subentityNameIter = subentityNameSet.iterator();
		while (subentityNameIter.hasNext()) {
			String subentityName = subentityNameIter.next();
			PaintPathSubentity subentity = subentityNameDataMap_.get(subentityName);
			if (subentity.effectHidden_) {
				continue;
			}
			subentity.cameraEffectAlphaMul_ = cameraEffectAlphaMul_;
			numSubentitiesDrawCalls_++;
			boolean didDraw = subentity.draw(canvas, paint, camera, entityCameraTransform);
			if (!didDraw) {
				numSubentitiesDrawFilteredLOD_++;
			}
		}
		
		Matrix aabbCameraTransform;
		aabbCameraTransform = camera.getViewportTransform(0, 0);
		aabbScreenCoord_.set(getAabb());
		aabbScreenCoord_.left += posX_ - (int)posX_;
		aabbScreenCoord_.right += posX_ - (int)posX_;
		aabbScreenCoord_.top += posY_ - (int)posY_;
		aabbScreenCoord_.bottom += posY_ - (int)posY_;
		
		aabbCameraTransform.mapRect(aabbScreenCoord_);
		
		if (aabbPath_ != null) {
			aabbPath_.rewind();
			aabbPath_.moveTo(getAabb().left, getAabb().top);
			aabbPath_.lineTo(getAabb().right, getAabb().top);
			aabbPath_.lineTo(getAabb().right, getAabb().bottom);
			aabbPath_.lineTo(getAabb().left, getAabb().bottom);
			aabbPath_.lineTo(getAabb().left, getAabb().top);
			
			aabbPath_.transform(aabbCameraTransform, aabbPath_);
			paint.setColor(aabbColor_);
			paint.setStrokeWidth(aabbLineWidth_);
			paint.setStyle(Paint.Style.STROKE);
			canvas.drawPath(aabbPath_, paint);
		}
		
		if (Config.getSingleton().showCollDebugInfo_) {
			showAabb();
		} else {
			hideAabb();
		}
		
		subentityNameDataMapAccessLock_ = false;
	}
	
	public Rect getAabb() {
		if (!isPositionSet()) {
			aabb_.setEmpty();
			return aabb_;
		}
		
		if (mesh_.aabb_.isEmpty()) {
			mesh_.computeAabb();
		}
		if (aabb_.isEmpty()) {
			aabb_.set(mesh_.aabb_);
			if (useScale_) {
				aabb_.left *= scaleX_;
				aabb_.right *= scaleX_;
				aabb_.top *= scaleY_;
				aabb_.bottom *= scaleY_;
			}
			aabb_.offset((int)posX_, (int)posY_);
		}
		return aabb_;
	}
	
	public void showAabb() {
		if (aabbPath_ == null) {
			aabbPath_ = new Path();
		}
	}
	
	public void hideAabb() {
		if (aabbPath_ != null) {
			aabbPath_ = null;
		}
	}
	
	public PaintPathSubentity getSubentity(String submeshName) {
		return subentityNameDataMap_.get(submeshName);
	}
	
	public boolean hasAnimTrack(String name) {
	    return mesh_.hasAnimTrack(name);
	}
	
	public PaintPathMesh.AnimTrack getAnimTrackFromMesh(String name) {
		return mesh_.getAnimTrack(name);
	}
	
	public void applyAnimTrackFromMesh(String name, float timePos, boolean resetPrevAnim) {
	    if (resetPrevAnim) {
	        resetAllAnimTransform();
	    }
		PaintPathMesh.AnimTrack animTrack = mesh_.getAnimTrack(name);
		PaintPathMesh.AnimTrack.TimeBound timeBound = animTrack.getTimeBound(timePos);
		if (!timeBound.isValid()) {
			// do not crash - frame rate is too log
			Logger.logInfo("warning - time bound is not valid, " + timeBound.startTime_
					+ ", " + timeBound.endTime_);
			return;
		}
		
		if (timeBound.endTime_ > timeBound.startTime_) {
			float timePercent = (timeBound.endTime_ - timePos) / (timeBound.endTime_ - timeBound.startTime_)
				* 100.f;
			applyAnimTrackItem(animTrack, timeBound.startTime_, (int)timePercent, resetPrevAnim);
			timePercent = (timePos - timeBound.startTime_) / (timeBound.endTime_ - timeBound.startTime_)
				* 100.f;
			applyAnimTrackItem(animTrack, timeBound.endTime_, (int)timePercent, resetPrevAnim);
		} else if (timeBound.endTime_ == timeBound.startTime_) {
			applyAnimTrackItem(animTrack, timeBound.startTime_, 100, resetPrevAnim);
		} else {
    		if (Config.ENABLE_DEBUGGING_KEYS) {
				throw new RuntimeException("anim track bounds " + timeBound.startTime_ + 
						", " + timeBound.endTime_ + " not handled");
    		} else {
    			return;
    		}
		}
	}
	
	private boolean applyAnimTrackItem(PaintPathMesh.AnimTrack animTrack, float itemTimePos, 
	        int percent, boolean resetPrevAnim) {
		if (!animTrack.hasAnimTrackItemList(itemTimePos)) {
			Log.e("H", "anim track at timePos " + itemTimePos + " not found");
			return false;
		}
		PaintPathMesh.AnimTrackItemList animTrackItemList = animTrack.getAnimTrackItemList(itemTimePos);
		Iterator<PaintPathMesh.AnimTrackItem> animTrackItemIter = animTrackItemList.iterator();
		while (animTrackItemIter.hasNext()) {
			PaintPathMesh.AnimTrackItem animTrackItem = animTrackItemIter.next();
			PaintPathSubentity subentity = getSubentity(animTrackItem.submeshName_);
	        if (!subentity.hasAnimKey(animTrackItem.animKeyName_)) {
	            throw new RuntimeException("anim key " + animTrackItem.animKeyName_ + " does not exist in submesh "
	                    + animTrackItem.submeshName_);
	        }
			subentity.applyAnimKeyFromSubmesh(animTrackItem.animKeyName_, percent, resetPrevAnim);
			applyAnimTransformToAllChildren(animTrackItem.submeshName_, subentity.getAnimTransform(),
			        resetPrevAnim);
		}
		return true;
	}
	
	public void resetAllAnimTransform() {
		Set<String> subentityNameSet = subentityNameDataMap_.keySet();
		Iterator<String> subentityNameIter = subentityNameSet.iterator();
		while (subentityNameIter.hasNext()) {
			String subentityName = subentityNameIter.next();
			PaintPathSubentity subentity = subentityNameDataMap_.get(subentityName);
			subentity.resetAnimTransform();
		}		
	}
	
	public void applyAnimTransformToAllChildren(String submeshName, Matrix parentTransform, boolean resetPrevAnim) {
		PaintPathSubmesh submesh = mesh_.getSubmesh(submeshName);
		Iterator<String> childSubmeshNameIter = submesh.childSubmeshNameList_.iterator();
		while (childSubmeshNameIter.hasNext()) {
			String childSubmeshName = childSubmeshNameIter.next();
			PaintPathSubentity childSubentity = getSubentity(childSubmeshName);
			childSubentity.applyAnimFromParentSubentity(parentTransform, resetPrevAnim);
			applyAnimTransformToAllChildren(childSubmeshName, parentTransform, resetPrevAnim);
		}
	}
	
	public void applyEffect(String name, int percent) {
		PaintPathMesh.Effect effect = mesh_.getEffect(name);
		if (effect == null) {
			throw new RuntimeException("No effect found with name " + name);
		}
		applyEffect(effect.type_, percent, effect.param1_, effect.param2_);
	}
	
	public void applyEffect(PaintPathMesh.EffectType type, int percent, String param1, String param2) {
		if (type == PaintPathMesh.EffectType.ENTITY_COLOR_MUL) {
			Set<String> subentityNameSet = subentityNameDataMap_.keySet();
			Iterator<String> subentityNameIter = subentityNameSet.iterator();
			while (subentityNameIter.hasNext()) {
				String subentityName = subentityNameIter.next();
				PaintPathSubentity subentity = subentityNameDataMap_.get(subentityName);
				subentity.applyEffectColorMul(percent, param1);
			}			
		} else if (type == PaintPathMesh.EffectType.SUBENTITY_COLOR_MUL) {
			String[] paramSplit2 = param2.split(" ");
			for (int i = 0; i < paramSplit2.length; i++) {
				PaintPathSubentity subentity = getSubentity(paramSplit2[i]);
				subentity.applyEffectColorMul(percent, param1);
			}
		} else if (type == PaintPathMesh.EffectType.MIRROR_HORZ) {
			Set<String> subentityNameSet = subentityNameDataMap_.keySet();
			Iterator<String> subentityNameIter = subentityNameSet.iterator();
			while (subentityNameIter.hasNext()) {
				String subentityName = subentityNameIter.next();
				PaintPathSubentity subentity = subentityNameDataMap_.get(subentityName);
				if (percent > 0) {
					subentity.applyEffectMirrowHorz_ = true;
				} else {
					subentity.applyEffectMirrowHorz_ = false;
				}
			}
		} else if (type == PaintPathMesh.EffectType.TORN_APART) {
			Set<String> subentityNameSet = subentityNameDataMap_.keySet();
			Iterator<String> subentityNameIter = subentityNameSet.iterator();
			while (subentityNameIter.hasNext()) {
				String subentityName = subentityNameIter.next();
				PaintPathSubentity subentity = subentityNameDataMap_.get(subentityName);
				if (subentity.effectExcludeFromTornApart_) {
					continue;
				}
				subentity.effectTornApartPercent_ = percent; 
			}
		} else if (type == PaintPathMesh.EffectType.GROUND_UNIT_UPSIDE_DOWN) {
			Set<String> subentityNameSet = subentityNameDataMap_.keySet();
			Iterator<String> subentityNameIter = subentityNameSet.iterator();
			while (subentityNameIter.hasNext()) {
				String subentityName = subentityNameIter.next();
				PaintPathSubentity subentity = subentityNameDataMap_.get(subentityName);
				subentity.effectGroundUnitUpsideDownPercent_ = percent; 
			}
		} else if (type == PaintPathMesh.EffectType.SHOW_HIDDEN) {
			PaintPathSubentity subentity = getSubentity(param1);
			if (percent > 0) {
				subentity.effectHidden_ = false;
			} else {
				subentity.effectHidden_ = true;
			}
		}
	}
	
	public void resetAllEffect() {
		Set<String> subentityNameSet = subentityNameDataMap_.keySet();
		Iterator<String> subentityNameIter = subentityNameSet.iterator();
		while (subentityNameIter.hasNext()) {
			String subentityName = subentityNameIter.next();
			PaintPathSubentity subentity = subentityNameDataMap_.get(subentityName);
			subentity.resetAllEffects();
		}
	}
	
	public void resetPosAndAabb() {
		posX_ = UnitStateBase.INVALID_FLOAT_POS; 
		aabb_.setEmpty();
	}
	
	protected void logDescription(String description) {
	    if (subentityNameDataMapAccessLock_) {
	        return;
	    }
	    
		Logger.logInfo("entity " + description);
		Set<String> subentityNameSet = subentityNameDataMap_.keySet();
		Iterator<String> subentityNameIter = subentityNameSet.iterator();
		while (subentityNameIter.hasNext()) {
			String subentityName = subentityNameIter.next();
			PaintPathSubentity subentity = subentityNameDataMap_.get(subentityName);
			subentity.logDescription("subentity " + subentityName + ":");
		}
	}
}
