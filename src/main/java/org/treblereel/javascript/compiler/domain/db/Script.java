/*
 * Copyright © 2025 Treblereel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.treblereel.javascript.compiler.domain.db;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "compiled_script",
        indexes = {
                @Index(name = "idx_script_hash", columnList = "hash"),
                @Index(name = "idx_script_name", columnList = "name"),
        }
)
public class Script extends PanacheEntity {

  private String name;
  private String filename;

  private String hash;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  public Instant createdAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id")
  private Person owner;

  public Script() {
  }

  public Script(String name, String filename, String hash, Person owner) {
    this.name = name;
    this.filename = filename;
    this.hash = hash;
    this.owner = owner;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public String getHash() {
    return hash;
  }

  public void setHash(String hash) {
    this.hash = hash;
  }

  public Person getOwner() {
    return owner;
  }

  public void setOwner(Person owner) {
    this.owner = owner;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Script script = (Script) o;
    return Objects.equals(name, script.name) && Objects.equals(filename, script.filename) && Objects.equals(hash, script.hash) && Objects.equals(createdAt, script.createdAt) && Objects.equals(owner, script.owner);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, filename, hash, createdAt, owner);
  }

  @Override
  public String toString() {
    return "Script{" +
            "name='" + name + '\'' +
            ", filename='" + filename + '\'' +
            ", hash='" + hash + '\'' +
            ", createdAt=" + createdAt +
            ", owner=" + owner +
            ", id=" + id +
            '}';
  }
}
