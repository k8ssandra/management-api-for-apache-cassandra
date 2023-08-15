/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class BaseEntity {

  @JsonProperty(value = "variant", required = false)
  public final Variant variant;

  @JsonProperty(value = "annotations", required = false)
  public final List<String> annotations;

  @JsonProperty(value = "mediaType", required = false)
  public final MediaType mediaType;

  @JsonProperty(value = "language", required = false)
  public final String language;

  @JsonProperty(value = "encoding", required = false)
  public final String encoding;

  @JsonCreator
  public BaseEntity(
      @JsonProperty("variant") Variant variant,
      @JsonProperty("annotations") List<String> annotations,
      @JsonProperty("mediaType") MediaType mediaType,
      @JsonProperty("language") String language,
      @JsonProperty("encoding") String encoding) {
    this.variant = variant;
    this.annotations = annotations;
    this.mediaType = mediaType;
    this.language = language;
    this.encoding = encoding;
  }

  @Override
  public int hashCode() {
    int hash = 5;
    hash = 83 * hash + Objects.hashCode(this.variant);
    hash = 83 * hash + Objects.hashCode(this.annotations);
    hash = 83 * hash + Objects.hashCode(this.mediaType);
    hash = 83 * hash + Objects.hashCode(this.language);
    hash = 83 * hash + Objects.hashCode(this.encoding);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final BaseEntity other = (BaseEntity) obj;
    if (!Objects.equals(this.variant, other.variant)) {
      return false;
    }
    if (!Objects.equals(this.annotations, other.annotations)) {
      return false;
    }
    if (!Objects.equals(this.mediaType, other.mediaType)) {
      return false;
    }
    if (!Objects.equals(this.language, other.language)) {
      return false;
    }
    return Objects.equals(this.encoding, other.encoding);
  }

  public static class MediaType {

    @JsonProperty(value = "type", required = false)
    public final String type;

    @JsonProperty(value = "subtype", required = false)
    public final String subtype;

    @JsonProperty(value = "parameters", required = false)
    public final Map<String, String> parameters;

    @JsonProperty(value = "wildcardType", required = false)
    public final String wildcardType;

    @JsonProperty(value = "wildcardSubtype", required = false)
    public final String wildcardSubtype;

    @JsonCreator
    public MediaType(
        @JsonProperty("type") String type,
        @JsonProperty("subtype") String subtype,
        @JsonProperty("parameters") Map<String, String> parameters,
        @JsonProperty("wildcardType") String wildcardType,
        @JsonProperty("wildcardSubtype") String wildcardSubtype) {
      this.type = type;
      this.subtype = subtype;
      this.parameters = parameters;
      this.wildcardType = wildcardType;
      this.wildcardSubtype = wildcardSubtype;
    }

    @Override
    public int hashCode() {
      int hash = 5;
      hash = 83 * hash + Objects.hashCode(this.type);
      hash = 83 * hash + Objects.hashCode(this.subtype);
      hash = 83 * hash + Objects.hashCode(this.parameters);
      hash = 83 * hash + Objects.hashCode(this.wildcardType);
      hash = 83 * hash + Objects.hashCode(this.wildcardSubtype);
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final MediaType other = (MediaType) obj;
      if (!Objects.equals(this.type, other.type)) {
        return false;
      }
      if (!Objects.equals(this.subtype, other.subtype)) {
        return false;
      }
      if (!Objects.equals(this.parameters, other.parameters)) {
        return false;
      }
      if (!Objects.equals(this.wildcardType, other.wildcardType)) {
        return false;
      }
      return Objects.equals(this.wildcardSubtype, other.wildcardSubtype);
    }
  }

  public static class Variant {

    @JsonProperty(value = "language", required = false)
    public final String language;

    @JsonProperty(value = "mediaType", required = false)
    public final MediaType mediaType;

    @JsonProperty(value = "encoding", required = false)
    public final String encoding;

    @JsonProperty(value = "languageString", required = false)
    public final String languageString;

    @JsonCreator
    public Variant(
        @JsonProperty("language") String language,
        @JsonProperty("mediaType") MediaType mediaType,
        @JsonProperty("encoding") String encoding,
        @JsonProperty("languageString") String languageString) {
      this.language = language;
      this.mediaType = mediaType;
      this.encoding = encoding;
      this.languageString = languageString;
    }

    @Override
    public int hashCode() {
      int hash = 5;
      hash = 83 * hash + Objects.hashCode(this.language);
      hash = 83 * hash + Objects.hashCode(this.mediaType);
      hash = 83 * hash + Objects.hashCode(this.encoding);
      hash = 83 * hash + Objects.hashCode(this.languageString);
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final Variant other = (Variant) obj;
      if (!Objects.equals(this.language, other.language)) {
        return false;
      }
      if (!Objects.equals(this.mediaType, other.mediaType)) {
        return false;
      }
      if (!Objects.equals(this.encoding, other.encoding)) {
        return false;
      }
      return Objects.equals(this.languageString, other.languageString);
    }
  }
}
