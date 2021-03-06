/*
* $Id: Family.java,v 3.1 2006/04/10 18:29:51 djbender Exp $
* WHITEHEAD INSTITUTE
* SOFTWARE COPYRIGHT NOTICE AGREEMENT
* This software and its documentation are copyright 2002 by the
* Whitehead Institute for Biomedical Research.  All rights are reserved.
*
* This software is supplied without any warranty or guaranteed support
* whatsoever.  The Whitehead Institute can not be responsible for its
* use, misuse, or functionality.
*/
package edu.mit.wi.pedfile;

import java.util.Hashtable;
import java.util.Enumeration;

/**
 * stores the familyName and the members of a family from a pedigree file
 *
 * this class is not thread safe (untested)
 *
 * @author Julian Maller
 */

public class Family {
    private Hashtable members;
    private String familyName;
    private int mendErrors;

    public Family(){
        this.members = new Hashtable();
    }

    public Family(String familyName){
        this.members = new Hashtable();
        this.familyName = familyName;
    }

	/**
	 * returns the name of this family (familyName)
	 * @return family name
	 */
    public String getFamilyName() {
        return familyName;
    }

    public boolean hasAncestor(String id){
        Individual ind = (Individual) members.get(id);
        if (ind != null){
            if (ind.getDadID().equals("0") && ind.getMomID().equals("0")){
                return false;
            }else{
                if (members.containsKey(ind.getDadID()) || members.containsKey(ind.getMomID())){
                    return true;
                }else{
                    return (hasAncestor(ind.getDadID()) || hasAncestor(ind.getMomID()));
                }
            }
        }else{
            return false;
        }
    }

	/**
	 * sets the family name (familyName)
	 * @param familyName
	 */
    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

	/**
	 * returns the members Hashtable (containing individuals)
	 * @return members Hashtable
	 */
    public Hashtable getMembers() {
        return members;
    }
   /**
    * sets the members hashtable
    * @param members
    */
    public void setMembers(Hashtable members) {
        this.members = members;
    }

    public int getMendErrs() {
        return mendErrors;
    }

    public void setMendErrs(int mendErrors) {
        this.mendErrors = mendErrors;
    }

    /**
     * returns the number of members of this family
     * @return number of members in this family
     */

    public int getNumMembers(){
        return this.members.size();
    }

	/**
	 * returns a list of individualIDs that are members of this family in the form of an enumeration
	 * @return enumeration memberlist
	 */
    public Enumeration getMemberList(){
        return this.members.keys();
    }

    /**
     * adds a member to this family (adds to members Vector)
     * @param ind Individual to add to members Vector
     */
    public void addMember(Individual ind){
        this.members.put(ind.getIndividualID(),ind);
    }

    public void removeMember(String id){
        members.remove(id);
    }

    public boolean containsMember(String id){
        if (members.containsKey(id)){
            return true;
        }else{
            return false;
        }
    }

    /**
     * get the Individual with individualID
     * @param individualID the individualID of the individual we want
     * @return the individual with matching individualID
     */
    public Individual getMember(String individualID) throws PedFileException{
        if (!(this.members.containsKey(individualID))){
            throw new PedFileException("Individual " + individualID +
                    " in family " + familyName + " is referenced, but appears to be missing.");
        }
        return (Individual)this.members.get(individualID);
    }


}
