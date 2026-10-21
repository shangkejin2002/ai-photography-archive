export default [
    {
        path: '/user/login',
        layout: false,
        component: './User/Login',
    },
    {
        path: '/user/register',
        layout: false,
        component: './User/Register',
    },
    {
        path: '/shared/:shareToken',
        layout: false,
        component: './Portfolio/Story',
    },
    {
        path: '/',
        redirect: '/library',
    },
    {
        path: '/library',
        wrappers: ['@/wrappers/Auth'],
        component: './Library',
    },
    {
        path: '/photos/:photoId',
        wrappers: ['@/wrappers/Auth'],
        component: './Photo/Detail',
    },
    {
        path: '/ai-create',
        wrappers: ['@/wrappers/Auth'],
        component: './AICreate',
    },
    {
        path: '/portfolios',
        wrappers: ['@/wrappers/Auth'],
        component: './Portfolios',
    },
    {
        path: '/portfolios/:portfolioId',
        wrappers: ['@/wrappers/Auth'],
        component: './Portfolio/Detail',
    },
    {
        path: '/portfolios/:portfolioId/story',
        layout: false,
        wrappers: ['@/wrappers/Auth'],
        component: './Portfolio/Story',
    },
    {
        path: '/insights',
        wrappers: ['@/wrappers/Auth'],
        component: './Insights',
    },
    {
        path: '/profile',
        wrappers: ['@/wrappers/Auth'],
        component: './Profile',
    },
    {
        path: '/admin',
        access: 'canAdmin',
        routes: [
            {
                path: '/admin/user',
                component: './Admin/User',
            },
        ],
    },
    {
        path: '*',
        component: '../../404',
    },
];
