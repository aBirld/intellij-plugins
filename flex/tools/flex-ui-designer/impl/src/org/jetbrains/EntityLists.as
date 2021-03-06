package org.jetbrains {
import flash.errors.IllegalOperationError;
import flash.utils.Dictionary;

public final class EntityLists {
  public static function add(list:Object, entity:Identifiable):void {
    var id:int = entity.id;
    const size:int = list is Dictionary ? int.MAX_VALUE : list.length;
    if (id < size) {
      if (list[id] != null) {
        throw new IllegalOperationError("Cannot add " + entity + " to " + list + " because id " + id + " is not free");
      }
    }
    else if (id > size) {
      list.length = id + 1;
    }

    list[id] = entity;
  }
}
}