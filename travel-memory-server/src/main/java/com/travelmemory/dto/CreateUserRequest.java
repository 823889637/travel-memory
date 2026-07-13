package com.travelmemory.dto;
import jakarta.validation.constraints.NotBlank;
public class CreateUserRequest { @NotBlank private String username; @NotBlank private String displayName; @NotBlank private String temporaryPassword;
 public String getUsername(){return username;} public void setUsername(String value){username=value;}
 public String getDisplayName(){return displayName;} public void setDisplayName(String value){displayName=value;}
 public String getTemporaryPassword(){return temporaryPassword;} public void setTemporaryPassword(String value){temporaryPassword=value;} }
