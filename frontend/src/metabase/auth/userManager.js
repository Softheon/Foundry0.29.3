import { createUserManager } from 'redux-oidc';
import Settings from "metabase/lib/settings";

const userManagerConfig = {
    client_id: Settings.get('google_auth_client_id'),
    redirect_uri: `${window.location.protocol}//${window.location.hostname}${window.location.port ? `:${window.location.port}` : ''}/auth/callback`,
    response_type: 'token id_token',
    scope: 'openid profile email metabase',
    authority: Settings.get('identity_server_uri'),
    silent_redirect_uri: `${window.location.protocol}//${window.location.hostname}${window.location.port ? `:${window.location.port}` : ''}/auth/silent_renew.html`,
    automaticSilentRenew: true,
    filterProtcolClaims: true,
    loadUserInfo: true,
    post_logout_redirect_uri: `${window.location.protocol}//${window.location.hostname}${window.location.port ? `:${window.location.port}` : ''}/auth/login`
};

const userManager = createUserManager(userManagerConfig);

export default userManager;