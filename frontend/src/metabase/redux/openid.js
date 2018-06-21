import { routerReducer } from 'react-router-redux';
import { combineReducers } from 'redux';
import { reducer as oidcReducer, SESSION_TERMINATED, USER_EXPIRED } from 'redux-oidc';

// const initialState = {
//     channels: []
// };

// export const LOAD_SUBSCRIPTIONS_START = 
//     "Foundry/LOAD_SUBSCRIPTIONS_START";

// export const LOAD_SUBSCRIPTIONS_SUCCESS = 
//     "Foundry/LOAD_SUBSCRIPTIONS_SUCCESS";

// export function loadSubscriptionsStart() {
//     return {
//         type: LOAD_SUBSCRIPTIONS_START
//     };
// }

// export function loadSubscriptionSuccess(channels) {
//     return {
//         type: LOAD_SUBSCRIPTIONS_SUCCESS,
//         payload: channels
//     };
// }

// export function subscriptionReducer(state = initialState, action) {
//     switch(action.type) {
//         case SESSION_TERMINATED:
//         case USER_EXPIRED:
//             return Object.assign({}, state, { channels: []});
//         case LOAD_SUBSCRIPTIONS_SUCCESS:
//             return Object.assign({}, state, {channels: action.payload});
//         default:
//             return state;
//     }
// }

const reducer = combineReducers(
    {
        // routing: routerReducer,
        oidc: oidcReducer,
        // subscriptions: subscriptionReducer
    }
);

export default reducer;