package com.sap.olingo.jpa.metadata.core.edm.mapper.api;

import java.util.List;

import com.sap.olingo.jpa.metadata.core.edm.mapper.exception.ODataJPAModelException;

public interface JPAEntityType extends JPAStructuredType {
  /**
   * 
   * @return Mime type of streaming content
   * @throws ODataJPAModelException
   */
  public String getContentType() throws ODataJPAModelException;

  public JPAPath getContentTypeAttributePath() throws ODataJPAModelException;

  /**
   * Returns a resolved list of all attributes that are marked as Id, so the attributes of an EmbeddedId are returned as
   * separate entries
   * @return
   * @throws ODataJPAModelException
   */
  public List<JPAAttribute> getKey() throws ODataJPAModelException;

  /**
   * Returns a list of path of all attributes annotated as Id. EmbeddedId are <b>not</b> resolved
   * @return
   * @throws ODataJPAModelException
   */
  public List<JPAPath> getKeyPath() throws ODataJPAModelException;

  /**
   * Returns the class of the Key. This could by either a primitive tape, the IdClass or the Embeddable of an EmbeddedId
   * @return
   */
  public Class<?> getKeyType();

  /**
   * 
   * @return
   * @throws ODataJPAModelException
   */
  public List<JPAPath> getSearchablePath() throws ODataJPAModelException;

  public JPAPath getStreamAttributePath() throws ODataJPAModelException;

  /**
   * 
   * @return Name of the database table
   */
  public String getTableName();

  public boolean hasEtag() throws ODataJPAModelException;

  public boolean hasStream() throws ODataJPAModelException;

  public List<JPAPath> searchChildPath(JPAPath selectItemPath);

}
