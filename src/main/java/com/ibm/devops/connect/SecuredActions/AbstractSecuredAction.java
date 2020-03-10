package com.ibm.devops.connect.SecuredActions;

import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import hudson.security.SecurityRealm;
import org.acegisecurity.userdetails.UserDetails;
import com.ibm.devops.connect.DevOpsGlobalConfiguration;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.userdetails.UsernameNotFoundException;

public abstract class AbstractSecuredAction {
    public static final String NO_CREDENTIALS_PROVIDED = "No Credentials Provided";

    protected abstract void run(ParamObj paramObj);

    public class ParamObj {
        private String jenkinsAuthenticationError;

        protected void setJenkinsAuthenticationError(String message) {
            this.jenkinsAuthenticationError = message;
        }

        public String getJenkinsAuthenticationError() {
            return this.jenkinsAuthenticationError;
        }

        public Boolean isJenkinsAuthenticationError() {
            if(this.jenkinsAuthenticationError != null) {
                return true;
            } else {
                return false;
            }
        }
    }

    public void runAsJenkinsUser(ParamObj paramObj) {

        StandardUsernamePasswordCredentials providedCredentials = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getCredentialsObj();

        Authentication originalAuth = null;

        if (providedCredentials != null) {
            originalAuth = Jenkins.getInstance().getAuthentication();
            try {
                Authentication authenticatedAuth = authenticateCredentials(providedCredentials);
                SecurityContextHolder.getContext().setAuthentication(authenticatedAuth);
            } catch (UsernameNotFoundException e) {
                paramObj.setJenkinsAuthenticationError("Bad Jenkins Credentials: Velocity configuration in Jenkins references Jenkins Credentials for a user that doesn't exist.");
                System.out.println("Bad Jenkins Credentials: Wrong username provided in Velocity configuration in Jenkins.");
            } catch (AuthenticationException e) {
                if ( e instanceof BadCredentialsException ) {
                    paramObj.setJenkinsAuthenticationError("Bad Jenkins Credentials: Wrong username or password provided in Velocity configuration in Jenkins.");
                    System.out.println("Bad Jenkins Credentials: Wrong username or password provided in Velocity configuration in Jenkins.");
                } else {
                    paramObj.setJenkinsAuthenticationError("Bad Jenkins Credentials");
                    System.out.println("Something else went wrong");
                }
            }
        } else {
            paramObj.setJenkinsAuthenticationError(NO_CREDENTIALS_PROVIDED);
        }

        try{
            run(paramObj);
        } finally {
            if (originalAuth != null) {
                SecurityContextHolder.getContext().setAuthentication(originalAuth);
            }
        }

    }

    private Authentication authenticateCredentials(StandardUsernamePasswordCredentials providedCredentials) throws AuthenticationException {
        SecurityRealm realm = Jenkins.getInstance().getSecurityRealm();
        SecurityRealm.SecurityComponents securityComponents = realm.createSecurityComponents();

        Authentication auth = getAuth(providedCredentials, realm);

        Authentication result = null;
        if(auth != null) {
            result = securityComponents.manager.authenticate(auth);
        }

        return result;
    }

    private Authentication getAuth(StandardUsernamePasswordCredentials providedCredentials, SecurityRealm realm) {
        UserDetails userDetails = realm.loadUserByUsername(providedCredentials.getUsername());

        userDetails.getAuthorities();

        Authentication auth = new UsernamePasswordAuthenticationToken(providedCredentials.getUsername(), providedCredentials.getPassword().getPlainText(), userDetails.getAuthorities());

        return auth;
    }
}