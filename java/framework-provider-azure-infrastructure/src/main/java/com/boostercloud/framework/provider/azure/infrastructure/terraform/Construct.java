package com.boostercloud.framework.provider.azure.infrastructure.terraform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A node of the construct tree, the subset of the {@code constructs} library that the Terraform synthesis relies on:
 * every construct has an id that is unique among its siblings, a path ({@code <stack>/<id>}), and children in creation
 * order. The ids of the direct children of a stack become Terraform logical names (see {@link LogicalIds}), so they are
 * part of the deployment contract.
 */
public abstract class Construct {

  private final Construct scope;
  private final String id;
  private final Map<String, Construct> children = new LinkedHashMap<>();

  /**
   * Creates a construct and adds it to its scope.
   *
   * @param scope the parent, {@code null} for the root
   * @param id the id, unique in the scope
   * @throws IllegalArgumentException {@code There is already a Construct with name '<id>' in <Type> [<scope id>]}
   *     when the scope already has a child with that id (the {@code constructs} message)
   */
  protected Construct(Construct scope, String id) {
    this.scope = scope;
    this.id = Objects.requireNonNull(id, "id");
    if (scope != null) {
      scope.addChild(this);
    }
  }

  private void addChild(Construct child) {
    if (children.containsKey(child.id)) {
      throw new IllegalArgumentException("There is already a Construct with name '" + child.id + "' in " + typeName()
          + (id.isEmpty() ? "" : " [" + id + "]"));
    }
    children.put(child.id, child);
  }

  /**
   * @return the name used in construct error messages ({@code this.constructor.name} in TypeScript)
   */
  protected String typeName() {
    return getClass().getSimpleName();
  }

  /**
   * @return the id of this construct ({@code node.id})
   */
  public String id() {
    return id;
  }

  /**
   * @return the parent construct, {@code null} for the root
   */
  public Construct scope() {
    return scope;
  }

  /**
   * @return the children in creation order
   */
  public List<Construct> children() {
    return List.copyOf(children.values());
  }

  /**
   * @return the constructs from the root to this one ({@code node.scopes})
   */
  public List<Construct> scopes() {
    List<Construct> scopes = new ArrayList<>();
    for (Construct current = this; current != null; current = current.scope) {
      scopes.add(current);
    }
    Collections.reverse(scopes);
    return scopes;
  }

  /**
   * @return the ids from the root to this construct joined with {@code /}, skipping empty ids ({@code node.path})
   */
  public String path() {
    List<String> ids = new ArrayList<>();
    for (Construct construct : scopes()) {
      if (!construct.id.isEmpty()) {
        ids.add(construct.id);
      }
    }
    return String.join("/", ids);
  }

  /**
   * @return this construct and all its descendants, depth first in creation order ({@code node.findAll()})
   */
  public List<Construct> findAll() {
    List<Construct> all = new ArrayList<>();
    collect(this, all);
    return all;
  }

  private static void collect(Construct construct, List<Construct> all) {
    all.add(construct);
    for (Construct child : construct.children.values()) {
      collect(child, all);
    }
  }
}
