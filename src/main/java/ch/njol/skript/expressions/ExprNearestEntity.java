package ch.njol.skript.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.entity.EntityData;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.util.Utils;
import ch.njol.util.Kleenean;
import ch.njol.util.StringUtils;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import ch.njol.skript.lang.util.SimpleExpression;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.Arrays;

@Name("Nearest Entity")
@Description("Gets the entity nearest to a location or another entity.")
@Examples({
	"kill the nearest pig and cow relative to player",
	"teleport player to the nearest cow relative to player",
	"teleport player to the nearest entity relative to player",
	"",
	"on click:",
	"\tkill nearest pig"
})
@Since("2.7")
public class ExprNearestEntity extends SimpleExpression<Entity> {

	static {
		Skript.registerExpression(ExprNearestEntity.class, Entity.class, ExpressionType.COMBINED,
				"[the] nearest %*entitydatas% [[relative] to %entity/location%]",
				"[the] %*entitydatas% nearest [to %entity/location%]");
	}

	@SuppressWarnings("NotNullFieldNotInitialized")
	private EntityData<?>[] entityDatas;

	@SuppressWarnings("NotNullFieldNotInitialized")
	private Expression<?> relativeTo;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		entityDatas = ((Literal<EntityData<?>>) exprs[0]).getArray();
		if (entityDatas.length != Arrays.stream(entityDatas).distinct().count()) {
			Skript.error("Entity list may not contain duplicate entities");
			return false;
		}
		relativeTo = exprs[1];
		return true;
	}

	@Override
	protected Entity[] get(Event event) {
		Object relativeTo = this.relativeTo.getSingle(event);
		if (relativeTo == null || (relativeTo instanceof Location && ((Location) relativeTo).getWorld() == null))
			return (Entity[]) Array.newInstance(this.getReturnType(), 0);;
		Entity[] nearestEntities = (Entity[]) Array.newInstance(this.getReturnType(), entityDatas.length);
		for (int i = 0; i < nearestEntities.length; i++) {
			if (relativeTo instanceof Entity) {
				nearestEntities[i] = getNearestEntity(entityDatas[i], ((Entity) relativeTo).getLocation(), (Entity) relativeTo);
			} else {
				nearestEntities[i] = getNearestEntity(entityDatas[i], (Location) relativeTo, null);
			}
		}
		return nearestEntities;
	}

	@Override
	public boolean isSingle() {
		return entityDatas.length == 1;
	}

	private transient @Nullable Class<? extends Entity> knownReturnType;

	@Override
	public Class<? extends Entity> getReturnType() {
		if (knownReturnType != null)
			return knownReturnType;
		Class<? extends Entity>[] types = new Class[entityDatas.length];
		for (int i = 0; i < types.length; i++) {
			types[i] = entityDatas[i].getType();
		}
		return knownReturnType = Utils.highestDenominator(Entity.class, types);
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return "nearest " + StringUtils.join(entityDatas) + " relative to " + relativeTo.toString(event, debug);
	}

	@Nullable
	private Entity getNearestEntity(EntityData<?> entityData, Location relativePoint, @Nullable Entity excludedEntity) {
		Entity nearestEntity = null;
		double nearestDistance = -1;
		for (Entity entity : relativePoint.getWorld().getEntitiesByClass(entityData.getType())) {
			if (entity != excludedEntity && entityData.isInstance(entity)) {
				double distance = entity.getLocation().distance(relativePoint);
				if (nearestEntity == null || distance < nearestDistance) {
					nearestDistance = distance;
					nearestEntity = entity;
				}
			}
		}
		return nearestEntity;
	}

}
