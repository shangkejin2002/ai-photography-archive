export default {
    dev: {
        '/api/': {
            target: process.env.API_PROXY_TARGET || 'http://localhost:8080',
            changeOrigin: true,
        },
    },
};
