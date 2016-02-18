package org.apache.olingo.jpa.processor.core.query;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.persistence.Tuple;
import javax.persistence.TupleElement;

import org.apache.olingo.commons.api.data.ComplexValue;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Link;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.ex.ODataRuntimeException;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAAttribute;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAEntityType;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAOnConditionItem;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAPath;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAStructuredType;
import org.apache.olingo.jpa.metadata.core.edm.mapper.exception.ODataJPAModelException;
import org.apache.olingo.jpa.metadata.core.edm.mapper.impl.JPAAssociationPath;
import org.apache.olingo.server.api.ODataApplicationException;

public abstract class JPATupleAbstractConverter {

  public static final String ACCESS_MODIFIER_GET = "get";
  public static final String ACCESS_MODIFIER_SET = "set";
  public static final String ACCESS_MODIFIER_IS = "is";
  private static final Map<String, HashMap<String, Method>> methodsBuffer =
      new HashMap<String, HashMap<String, Method>>();
  protected final JPAEntityType jpaConversionTargetEntity;
  protected final JPAExpandResult jpaQueryResult;

  public JPATupleAbstractConverter(final JPAEntityType jpaEntity, final JPAExpandResult jpaQueryResult) {
    super();
    this.jpaConversionTargetEntity = jpaEntity;
    this.jpaQueryResult = jpaQueryResult;
  }

  protected String buildConcatenatedKey(final Tuple row, final List<JPAOnConditionItem> joinColumns) {
    final StringBuffer buffer = new StringBuffer();
    for (final JPAOnConditionItem item : joinColumns) {
      buffer.append(JPAPath.PATH_SEPERATOR);
      buffer.append(row.get(item.getLeftPath().getAlias()));
    }
    buffer.deleteCharAt(0);
    return buffer.toString();
  }

  protected Entity convertRow(final JPAEntityType rowEntity, final Tuple row) throws ODataApplicationException {
    final Map<String, ComplexValue> complexValueBuffer = new HashMap<String, ComplexValue>();
    final Entity odataEntity = new Entity();
    odataEntity.setType(rowEntity.getExternalFQN().getFullQualifiedNameAsString());
    final List<Property> properties = odataEntity.getProperties();
    for (final TupleElement<?> element : row.getElements()) {
      try {
        convertAttribute(row.get(element.getAlias()), element.getAlias(), "", rowEntity, complexValueBuffer,
            properties);
      } catch (ODataJPAModelException e) {
        throw new ODataApplicationException("Mapping Error", 500, Locale.ENGLISH, e);
      }
    }
    try {
      odataEntity.setId(createId(jpaConversionTargetEntity.getKey(), row));
    } catch (ODataJPAModelException e) {
      throw new ODataApplicationException("Property not found", HttpStatusCode.BAD_REQUEST.getStatusCode(),
          Locale.ENGLISH, e);
    }
    for (final String attribute : complexValueBuffer.keySet()) {
      final ComplexValue complexValue = complexValueBuffer.get(attribute);
      complexValue.getNavigationLinks().addAll(createExpand(row, odataEntity.getId(), attribute));
    }
    odataEntity.getNavigationLinks().addAll(createExpand(row, odataEntity.getId(), ""));
    return odataEntity;
  }

  protected Collection<? extends Link> createExpand(final Tuple row, final URI uri, final String attributeName)
      throws ODataApplicationException {
    final List<Link> entityExpandLinks = new ArrayList<Link>();
    // jpaConversionTargetEntity.
    final Map<JPAAssociationPath, JPAExpandResult> children = jpaQueryResult.getChildren();
    if (children != null) {
      for (final JPAAssociationPath associationPath : children.keySet()) {
        try {
          JPAStructuredType type;
          if (attributeName != null && !attributeName.isEmpty()) {
            type = ((JPAAttribute) jpaConversionTargetEntity.getPath(attributeName).getPath().get(0))
                .getStructuredType();
          } else
            type = jpaConversionTargetEntity;
          if (type.getDeclaredAssociation(associationPath.getLeaf().getExternalName()) != null) {
            final Link expand = new JPATupleExpandResultConverter(uri, children.get(associationPath), row,
                associationPath).getResult();
            entityExpandLinks.add(expand);
          }
        } catch (ODataJPAModelException e) {
          throw new ODataApplicationException("Navigation property not found", HttpStatusCode.INTERNAL_SERVER_ERROR
              .ordinal(), Locale.ENGLISH, e);
        }
      }
    }
    return entityExpandLinks;
  }

  protected abstract URI createId(List<? extends JPAAttribute> keyAttributes, Tuple row)
      throws ODataApplicationException, ODataRuntimeException;

  protected Map<String, Method> getGetter(final JPAAttribute structuredAttribute) {
    HashMap<String, Method> pojoMethods = methodsBuffer.get(structuredAttribute.getInternalName());
    if (pojoMethods == null) {
      pojoMethods = new HashMap<String, Method>();
      final Method[] allMethods = structuredAttribute.getStructuredType().getTypeClass().getMethods();
      for (final Method m : allMethods) {
        pojoMethods.put(m.getName(), m);
      }
      methodsBuffer.put(structuredAttribute.getInternalName(), pojoMethods);
    }
    return pojoMethods;
  }

  void convertAttribute(final Object value, final String externalName, final String prefix,
      final JPAStructuredType jpaStructuredType, final Map<String, ComplexValue> complexValueBuffer,
      final List<Property> properties) throws ODataJPAModelException {

    ComplexValue compexValue = null;
    if (jpaStructuredType.getPath(externalName) != null) {
      final JPAAttribute attribute = (JPAAttribute) jpaStructuredType.getPath(externalName).getPath().get(0);// getLeaf();
      if (attribute != null && attribute.isComplex()) {
        String bufferKey;
        if (prefix.isEmpty())
          bufferKey = attribute.getExternalName();
        else
          bufferKey = prefix + JPAPath.PATH_SEPERATOR + attribute.getExternalName();
        compexValue = complexValueBuffer.get(bufferKey);
        if (compexValue == null) {
          compexValue = new ComplexValue();
          complexValueBuffer.put(bufferKey, compexValue);
          properties.add(new Property(
              attribute.getStructuredType().getExternalFQN().getFullQualifiedNameAsString(),
              attribute.getExternalName(),
              ValueType.COMPLEX,
              compexValue));
        }
        final List<Property> values = compexValue.getValue();
        final int splitIndex = attribute.getExternalName().length() + JPAPath.PATH_SEPERATOR.length();
        final String attributeName = externalName.substring(splitIndex);
        convertAttribute(value, attributeName, bufferKey, attribute.getStructuredType(), complexValueBuffer, values);
      } else {
        // ...$select=Name1,Address/Region
        properties.add(new Property(
            null,
            externalName,
            ValueType.PRIMITIVE,
            value));
      }
    }
  }

}