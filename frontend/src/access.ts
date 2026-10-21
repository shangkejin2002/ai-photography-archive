import type { InitialState } from './app';

export default function access(initialState?: InitialState) {
    const user = initialState?.currentUser;

    return {
        isLogin: !!user?.id,
        canAdmin: user?.userRole === 1,
    };
}
