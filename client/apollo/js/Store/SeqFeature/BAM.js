define( [
        'dojo/_base/declare',
        'dojo/_base/array',
        'dojo/_base/Deferred',
        'dojo/_base/lang',
        'JBrowse/has',
        'JBrowse/Util',
        'WebApollo/JSONUtils',
        'WebApollo/ProjectionUtils',
        'JBrowse/Errors',
        'JBrowse/Model/XHRBlob',
        'JBrowse/Store/LRUCache',
        'JBrowse/Store/SeqFeature',
        'JBrowse/Store/DeferredStatsMixin',
        'JBrowse/Store/DeferredFeaturesMixin',
        'WebApollo/Store/SeqFeature/GlobalStatsEstimationMixin',
        'WebApollo/Store/SeqFeature/BAM/File'
    ],
    function(
        declare,
        array,
        Deferred,
        lang,
        has,
        Util,
        JSONUtils,
        ProjectionUtils,
        Errors,
        XHRBlob,
        LRUCache,
        SeqFeatureStore,
        DeferredStatsMixin,
        DeferredFeaturesMixin,
        GlobalStatsEstimationMixin,
        WebApolloBAMFile
    ) {

    return declare([ SeqFeatureStore, DeferredStatsMixin, DeferredFeaturesMixin, GlobalStatsEstimationMixin ], {

        constructor: function(args) {

            var bamBlob = args.bam ||
                new XHRBlob( this.resolveUrl(
                        args.urlTemplate || 'data.bam'
                    )
                );

            var baiBlob = args.bai ||
                new XHRBlob( this.resolveUrl(
                        args.baiUrlTemplate || ( args.urlTemplate ? args.urlTemplate+'.bai' : 'data.bam.bai' )
                    )
                );

            this.bam = new WebApolloBAMFile({
                store: this,
                data: bamBlob,
                bai: baiBlob,
                chunkSizeLimit: args.chunkSizeLimit
            });

            this.source = ( bamBlob.url  ? bamBlob.url.match( /\/([^/\#\?]+)($|[\#\?])/ )[1] :
                    bamBlob.blob ? bamBlob.blob.name : undefined ) || undefined;

            if( ! has( 'typed-arrays' ) ) {
                this._failAllDeferred( 'This web browser lacks support for JavaScript typed arrays.' );
                return;
            }

            this.bam.init({
                success: lang.hitch( this,
                    function() {
                        this._deferred.features.resolve({success:true});

                        this._estimateGlobalStats()
                            .then( lang.hitch(
                                this,
                                function( stats ) {
                                    this.globalStats = stats;
                                    this._deferred.stats.resolve({success:true});
                                }
                                ),
                                lang.hitch( this, '_failAllDeferred' )
                            );
                    }),
                failure: lang.hitch( this, '_failAllDeferred' )
            });

            this.storeTimeout = args.storeTimeout || 3000;


            // replace _fetchChunkFeatures with few changes
            this.bam._fetchChunkFeatures = function( chunks, chrId, min, max, featCallback, endCallback, errorCallback ) {
                var thisB = this;

                if( ! chunks.length ) {
                    endCallback();
                    return;
                }

                var chunksProcessed = 0;

                var cache = this.featureCache = this.featureCache || new LRUCache({
                        name: 'bamFeatureCache',
                        fillCallback: dojo.hitch( this, '_readChunk' ),
                        sizeFunction: function( features ) {
                            return features.length;
                        },
                        maxSize: 100000 // cache up to 100,000 BAM features
                    });

                // check the chunks for any that are over the size limit.  if
                // any are, don't fetch any of them
                for( var i = 0; i<chunks.length; i++ ) {
                    var size = chunks[i].fetchedSize();
                    if( size > this.chunkSizeLimit ) {
                        errorCallback( new Errors.DataOverflow('Too many BAM features. BAM chunk size '+Util.commifyNumber(size)+' bytes exceeds chunkSizeLimit of '+Util.commifyNumber(this.chunkSizeLimit)+'.' ) );
                        return;
                    }
                }

                var haveError;
                var pastStart;
                array.forEach( chunks, function( c ) {
                    cache.get( c, function( f, e ) {
                        if( e && !haveError )
                            errorCallback(e);
                        if(( haveError = haveError || e )) {
                            return;
                        }

                        for( var i = 0; i<f.length; i++ ) {
                            var feature = f[i];
                            if( feature._refID == chrId ) {
                                // on the right ref seq
                                var start = feature.isProjected ? feature.get('_original_start') : feature.get('start');
                                var end = feature.isProjected ? feature.get('_original_end') : feature.get('end');
                                if( start > max ) // past end of range, can stop iterating
                                    break;
                                else if( end >= min ) // must be in range
                                    featCallback( feature );
                            }
                        }
                        if( ++chunksProcessed == chunks.length ) {
                            endCallback();
                        }
                    });
                });
            }
        },


        /**
         * Override getFeatures to support BAM querying in a projected space.
         */
        getFeatures: function(query, featCallback, endCallback, errorCallback) {
            // parse sequenceList from query.ref
            var sequenceListObject = ProjectionUtils.parseSequenceList(query.ref);
            // unproject start and end
            var featureLocationArray = ProjectionUtils.unProjectCoordinates(query.ref, query.start, query.end);
            // rebuild the query
            query.ref = sequenceListObject[0].name;
            query.start = featureLocationArray[0];
            query.end = featureLocationArray[1];
            this._deferred.features.then(
                dojo.hitch( this, '_getFeatures', query, featCallback, endCallback, errorCallback ),
                errorCallback
            );
        },

        /**
         * Interrogate whether a store has data for a given reference
         * sequence.  Calls the given callback with either true or false.
         *
         * Implemented as a binary interrogation because some stores are
         * smart enough to regularize reference sequence names, while
         * others are not.
         */
        hasRefSeq: function( seqName, callback, errorCallback ) {
            var thisB = this;
            seqName = thisB.browser.regularizeReferenceName( seqName );
            this._deferred.stats.then( function() {
                callback( seqName in thisB.bam.chrToIndex );
            }, errorCallback );
        },

        // called by getFeatures from the DeferredFeaturesMixin
        _getFeatures: function( query, featCallback, endCallback, errorCallback ) {
            this.bam.fetch( query.ref ? query.ref : this.refSeq.name, query.start, query.end, featCallback, endCallback, errorCallback );
        },

        saveStore: function() {
            return {
                urlTemplate: this.config.bam.url,
                baiUrlTemplate: this.config.bai.url
            };
        }

    });

});