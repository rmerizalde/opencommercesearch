var src = 'ProductApi.js';

module.exports = function(grunt) {
    grunt.initConfig({
        pkg: grunt.file.readJSON('package.json'),
        jshint: {
            src: [src, 'Gruntfile.js'],
            options: {
                reporter: require('jshint-stylish'),
                asi: true,
                boss: true,
                browser: true,
                curly: true,
                eqnull: true,
                evil: true,
                expr: true,
                latedef: true,
                maxerr: 100,
                newcap: true,
                noarg: true,
                noempty: true,
                shadow: true,
                laxbreak: true,
                globals: {
                    define: true
                }
            }
        },
        watch: {
            files: [src, 'Gruntfile.js', 'test/*.js', '!**/node_modules/**/*'],
            tasks: ['jshint', 'mochacli']
        },
        mochacli: {
            options: {
                bail: true
            },
            all: ['test/*.js']
        }
    });

    grunt.loadNpmTasks('grunt-contrib-jshint');
    grunt.loadNpmTasks('grunt-contrib-watch');
    grunt.loadNpmTasks('grunt-mocha-cli');

    grunt.registerTask('default', [
        'jshint',
        'mochacli',
        'watch'
    ]);

    grunt.registerTask('test', [
        'jshint',
        'mochacli'
    ]);
};