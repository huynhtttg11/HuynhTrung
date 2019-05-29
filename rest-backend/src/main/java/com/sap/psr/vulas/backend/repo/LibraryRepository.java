package com.sap.psr.vulas.backend.repo;


import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sap.psr.vulas.backend.model.AffectedConstructChange;
import com.sap.psr.vulas.backend.model.Bug;
import com.sap.psr.vulas.backend.model.ConstructId;
import com.sap.psr.vulas.backend.model.Library;
import com.sap.psr.vulas.backend.util.ResultSetFilter;
import com.sap.psr.vulas.shared.enums.ConstructType;
import com.sap.psr.vulas.shared.enums.Scope;

/**
 * See here for JPQL: http://docs.oracle.com/javaee/6/tutorial/doc/bnbtg.html
 *
 */
@Repository
//@RepositoryRestResource(collectionResourceRel = "bugss", path = "bugss")
public interface LibraryRepository extends CrudRepository<Library, Long>, LibraryRepositoryCustom {
	
	public static final ResultSetFilter<Library> FILTER = new ResultSetFilter<Library>();

	@Query("SELECT l FROM Library l LEFT OUTER JOIN FETCH l.libraryId WHERE l.id=:id")
	List<Library> findById(@Param("id") Long id);
	
	@Query("SELECT l FROM Library l LEFT OUTER JOIN FETCH l.libraryId WHERE l.digest=:digest")
	List<Library> findByDigest(@Param("digest") String digest);
	
	
	@Query(value="SELECT COUNT(*) FROM "
			+ " lib_constructs  "
			+ "  WHERE library_id =:id ",nativeQuery=true)
	Integer countConstructOfLibrary(@Param("id") Long id);
	
	@Query(value="SELECT COUNT(distinct app) FROM "
			+ " app_dependency  "
			+ "  WHERE lib =:digest and transitive=false ",nativeQuery=true)
	Integer countUsages(@Param("digest") String digest);
	
//	@Query(value="SELECT COUNT(distinct d.app) FROM "
//			+ " Dependency d  "
//			+ " WHERE lib =:digest and d.transitive=false AND d.scope NOT IN :exclScopes")
//	Integer countUsages(@Param("digest") String digest,@Param("exclScopes") Dependency.Scope[] exclScopes);
	
	@Query(value="select t.lib from (SELECT COUNT(distinct app) as c,lib  FROM "
			+ "    app_dependency   "
			+ "   WHERE  transitive=false group by lib order by c desc) as t limit :num",nativeQuery=true)
	List<String> findMostUsed(@Param("num") Integer num);
	
	@Query(value=" SELECT d.lib.digest,COUNT(distinct d.app)  FROM "
			+ "   Dependency d  "
			+ "   WHERE  transitive=false "
			+ "   AND d.scope NOT IN :exclScopes group by d.lib.digest order by COUNT(distinct d.app) desc")
	List<Object[]> findMostUsed(@Param("exclScopes") Scope[] exclScopes);
	
	/**
	 * Returns all {@link Bug}s relevant for the {@link Library} with the given digest.
	 * A {@link Bug} is considered relevant if one or more of the {@link ConstructId}s
	 * changed as part of the bug fix is contained in the {@link Library}.
	 * @param digest
	 * @return
	 */
	@Query("SELECT"
			+ " DISTINCT b FROM"
			+ "   Bug b"
			+ "   JOIN b.constructChanges cc,"
			+ "   Library l"
			+ "   JOIN l.constructs lc"
			+ " WHERE"
			+ "       cc.constructId = lc"
			+ "   AND l.digest = :digest")
	List<Bug> findBugs(@Param("digest") String digest);
	
	/**
	 * Same as {@link LibraryRepository#findBugs(String)}, but offering an additional filter for selected bug ID(s).
	 * @param digest
	 * @param selectedBugs
	 * @return
	 */
	@Query("SELECT"
			+ " DISTINCT b FROM"
			+ "   Bug b"
			+ "   JOIN b.constructChanges cc,"
			+ "   Library l"
			+ "   JOIN l.constructs lc"
			+ " WHERE"
			+ "       cc.constructId = lc"
			+ "   AND l.digest = :digest"
			+ "   AND b.bugId IN :bugIds") // filter clause on bugId
	List<Bug> findBugs(@Param("digest") String digest, @Param("bugIds") String[] bugIds);
	
	/**
	 * Returns all {@link ConstructId}s for the {@link Library} with the given digest and the bug with
	 * the given ID that exist both in the library and the bug's change list.
	 * @param digest
	 * @return
	 */
	@Query("SELECT"
			+ " DISTINCT lc FROM"
			+ "   Bug b"
			+ "   JOIN b.constructChanges cc,"
			+ "   Library l"
			+ "   JOIN l.constructs lc"
			+ " WHERE"
			+ "       cc.constructId = lc"
			+ "   AND l.digest  = :digest"
			+ "   AND b.bugId = :bugId")
	List<ConstructId> findBuggyConstructIds(@Param("digest") String digest, @Param("bugId") String bugId);
	
	/**
	 * Returns all {@link ConstructId}s contained in the {@link Library} with the given digest.
	 * @param digest
	 * @return
	 */
	@Query("SELECT"
			+ " DISTINCT lc FROM"
			+ "   Library l"
			+ "   JOIN l.constructs lc"
			+ " WHERE"
			+ "   l.digest = :digest")
	List<ConstructId> findConstructIds(@Param("digest") String digest);

	/**
	 * Returns all {@link ConstructId}s contained in the {@link Library} with the given digest and of the given type.
	 * @param digest
	 * @return
	 */
	@Query("SELECT"
			+ " DISTINCT lc FROM"
			+ "   Library l"
			+ "   JOIN l.constructs lc"
			+ " WHERE"
			+ "   l.digest = :digest AND lc.type=:type")
	List<ConstructId> findConstructIdsOfType(@Param("digest") String digest,@Param("type")ConstructType type);

	
	@Query("SELECT"
			+ " DISTINCT affcc FROM"
			+ "   AffectedConstructChange affcc"
			+ "   JOIN affcc.affectedLib afflib,"
			+ "	  Bug b"
			+ "   JOIN b.constructChanges cc"
			+ " WHERE"
			+ "       affcc.cc = cc"
			+ "   AND afflib.lib.digest = :digest"
			+ "   AND b.bugId = :bugId")
	List<AffectedConstructChange> findAffCCs(@Param("digest") String digest,@Param("bugId") String bugId);
	
	@Query("SELECT"
			+ "   DISTINCT l FROM"
			+ "	  Library l "
			+ "   LEFT OUTER JOIN FETCH"
			+ "   l.libraryId lid "
			+ "   JOIN "
			+ "   l.constructs lc,"
			+ "	  Bug b"
			+ "   JOIN "
			+ "   b.constructChanges cc "
			+ "	  WHERE b.bugId = :bugId"
			+ "   AND lc = cc.constructId"
			+ "   AND (NOT lc.type='PACK' "                        // Java + Python exception
			+ "   OR NOT EXISTS (SELECT 1 FROM ConstructChange cc1 JOIN cc1.constructId c1 WHERE cc1.bug=cc.bug AND NOT c1.type='PACK' AND NOT c1.qname LIKE '%test%' AND NOT c1.qname LIKE '%Test%' and NOT cc1.constructChangeType='ADD') ) "     
			+ "   AND NOT (lc.type='MODU' AND lc.qname='setup')" // Python-specific exception: setup.py is virtually everywhere, considering it would bring far too many FPs
			)
		List<Library> findJPQLVulnerableLibrariesByBug(@Param("bugId") String bugId);
	
//	@Query(value= "select distinct s.digest from " + 
//			"(select y.digest,y.library_id_id,y.m,i.n from " +
//"(select v.digest as digest,v.library_id_id,count(u.id) as m from "+ 
//"(select distinct a.digest,a.library_id_id,d.id  from lib as a join lib_constructs as b on a.id=b.lib_id join construct_id as d on b.constructs_id=d.id where d.type='PACK' ) as v "+ 
//"join "+ 
//"(select c.id from lib as l join lib_constructs as lc on l.id=lc.lib_id join construct_id as c on lc.constructs_id=c.id where l.digest=:digest and c.type='PACK') as u on v.id=u.id group by v.digest,v.library_id_id) as y , "+
//" (select count(*) as n from lib as l join lib_constructs as lc on l.id=lc.lib_id join construct_id as c on lc.constructs_id=c.id "+ 
//"where l.digest=:digest and c.type='PACK' ) as i "+
// "where (y.m > (i.n *80/100) and  y.m < (i.n *120/100))) as s join library_id as lid on s.library_id_id=lid.id ",nativeQuery=true)
	@Query(value= "select distinct s.digest " + 
			" from " + 
			" (select candidate.digest, candidate.id, candidate.mvn_group,candidate.artifact,candidate.version,count(*) as candidate_pack_count from " +  
			" (select distinct a.digest,a.id,a.mvn_group,a.artifact,a.version  " + 
			" from " + 
			" (select distinct l1.digest,l1.id,lid1.mvn_group,lid1.artifact,lid1.version,c1.id  as cid " + 
			" from lib as l1  " + 
			" join library_id lid1 on l1.library_id_id=lid1.id " + 
			" join lib_constructs as lc1 on l1.id=lc1.library_id  " + 
			" join construct_id as c1 on lc1.constructs_id=c1.id where c1.type='PACK' ) as a " + 
			" join " + 
			" (select c.id  " + 
			" from lib as l " +  
			" join lib_constructs as lc on l.id=lc.library_id " +  
			" join construct_id as c on lc.constructs_id=c.id  " + 
			" where l.digest=:digest and c.type='PACK') as unknown " + 
			" on a.cid=unknown.id) as candidate " + 
			" join lib_constructs as lc3 on candidate.id=lc3.library_id " +  
			" join construct_id as c3 on lc3.constructs_id=c3.id  " + 
			" where c3.type='PACK' group by candidate.digest,candidate.id,candidate.mvn_group,candidate.artifact,candidate.version) as s, " + 
			" (select count(*) as unknown_pack_count " + 
			" from lib as l2  " + 
			" join lib_constructs as lc2 on l2.id=lc2.library_id " +  
			" join construct_id as c2 on lc2.constructs_id=c2.id  " + 
			" where l2.digest=:digest and c2.type='PACK') as unknown_count " + 
			" where (s.candidate_pack_count >= unknown_count.unknown_pack_count*90/100) AND (s.candidate_pack_count <= unknown_count.unknown_pack_count*100/100) ",nativeQuery=true)
	List<String> findDigestSamePack(@Param("digest") String digest);

}