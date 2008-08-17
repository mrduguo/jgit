/*
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Git Development Community nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.spearce.jgit.transport;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.spearce.jgit.errors.NotSupportedException;
import org.spearce.jgit.errors.TransportException;
import org.spearce.jgit.lib.NullProgressMonitor;
import org.spearce.jgit.lib.ProgressMonitor;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.Repository;

/**
 * Connects two Git repositories together and copies objects between them.
 * <p>
 * A transport can be used for either fetching (copying objects into the
 * caller's repository from the remote repository) or pushing (copying objects
 * into the remote repository from the caller's repository). Each transport
 * implementation is responsible for the details associated with establishing
 * the network connection(s) necessary for the copy, as well as actually
 * shuffling data back and forth.
 * <p>
 * Transport instances and the connections they create are not thread-safe.
 * Callers must ensure a transport is accessed by only one thread at a time.
 */
public abstract class Transport {
	/**
	 * Open a new transport instance to connect two repositories.
	 * 
	 * @param local
	 *            existing local repository.
	 * @param remote
	 *            location of the remote repository.
	 * @return the new transport instance. Never null.
	 * @throws URISyntaxException
	 *             the location is not a remote defined in the configuration
	 *             file and is not a well-formed URL.
	 * @throws NotSupportedException
	 *             the protocol specified is not supported.
	 */
	public static Transport open(final Repository local, final String remote)
			throws NotSupportedException, URISyntaxException {
		final RemoteConfig cfg = new RemoteConfig(local.getConfig(), remote);
		final List<URIish> uris = cfg.getURIs();
		if (uris.size() == 0)
			return open(local, new URIish(remote));
		return open(local, cfg);
	}

	/**
	 * Open a new transport instance to connect two repositories.
	 * 
	 * @param local
	 *            existing local repository.
	 * @param cfg
	 *            configuration describing how to connect to the remote
	 *            repository.
	 * @return the new transport instance. Never null.
	 * @throws NotSupportedException
	 *             the protocol specified is not supported.
	 * @throws IllegalArgumentException
	 *             if provided remote configuration doesn't have any URI
	 *             associated.
	 */
	public static Transport open(final Repository local, final RemoteConfig cfg)
			throws NotSupportedException {
		if (cfg.getURIs().isEmpty())
			throw new IllegalArgumentException(
					"Remote config \""
					+ cfg.getName() + "\" has no URIs associated");
		final Transport tn = open(local, cfg.getURIs().get(0));
		tn.setOptionUploadPack(cfg.getUploadPack());
		tn.fetch = cfg.getFetchRefSpecs();
		tn.tagopt = cfg.getTagOpt();
		tn.setOptionReceivePack(cfg.getReceivePack());
		tn.push = cfg.getPushRefSpecs();
		return tn;
	}

	/**
	 * Open a new transport instance to connect two repositories.
	 * 
	 * @param local
	 *            existing local repository.
	 * @param remote
	 *            location of the remote repository.
	 * @return the new transport instance. Never null.
	 * @throws NotSupportedException
	 *             the protocol specified is not supported.
	 */
	public static Transport open(final Repository local, final URIish remote)
			throws NotSupportedException {
		if (TransportGitSsh.canHandle(remote))
			return new TransportGitSsh(local, remote);

		else if (TransportHttp.canHandle(remote))
			return new TransportHttp(local, remote);

		else if (TransportSftp.canHandle(remote))
			return new TransportSftp(local, remote);

		else if (TransportGitAnon.canHandle(remote))
			return new TransportGitAnon(local, remote);

		else if (TransportAmazonS3.canHandle(remote))
			return new TransportAmazonS3(local, remote);

		else if (TransportBundle.canHandle(remote))
			return new TransportBundle(local, remote);

		else if (TransportLocal.canHandle(remote))
			return new TransportLocal(local, remote);

		throw new NotSupportedException("URI not supported: " + remote);
	}

	/**
	 * Default setting for {@link #fetchThin} option.
	 */
	public static final boolean DEFAULT_FETCH_THIN = true;

	/**
	 * Default setting for {@link #pushThin} option.
	 */
	public static final boolean DEFAULT_PUSH_THIN = false;

	/**
	 * Specification for fetch or push operations, to fetch or push all tags.
	 * Acts as --tags.
	 */
	public static final RefSpec REFSPEC_TAGS = new RefSpec(
			"refs/tags/*:refs/tags/*");

	/**
	 * Specification for push operation, to push all refs under refs/heads. Acts
	 * as --all.
	 */
	public static final RefSpec REFSPEC_PUSH_ALL = new RefSpec(
			"refs/heads/*:refs/heads/*");

	/** The repository this transport fetches into, or pushes out of. */
	protected final Repository local;

	/** The URI used to create this transport. */
	protected final URIish uri;

	/** Name of the upload pack program, if it must be executed. */
	private String optionUploadPack = RemoteConfig.DEFAULT_UPLOAD_PACK;

	/** Specifications to apply during fetch. */
	private List<RefSpec> fetch = Collections.emptyList();

	/**
	 * How {@link #fetch(ProgressMonitor, Collection)} should handle tags.
	 * <p>
	 * We default to {@link TagOpt#NO_TAGS} so as to avoid fetching annotated
	 * tags during one-shot fetches used for later merges. This prevents
	 * dragging down tags from repositories that we do not have established
	 * tracking branches for. If we do not track the source repository, we most
	 * likely do not care about any tags it publishes.
	 */
	private TagOpt tagopt = TagOpt.NO_TAGS;

	/** Should fetch request thin-pack if remote repository can produce it. */
	private boolean fetchThin = DEFAULT_FETCH_THIN;

	/** Name of the receive pack program, if it must be executed. */
	private String optionReceivePack = RemoteConfig.DEFAULT_RECEIVE_PACK;

	/** Specifications to apply during push. */
	private List<RefSpec> push = Collections.emptyList();

	/** Should push produce thin-pack when sending objects to remote repository. */
	private boolean pushThin = DEFAULT_PUSH_THIN;

	/**
	 * Create a new transport instance.
	 * 
	 * @param local
	 *            the repository this instance will fetch into, or push out of.
	 *            This must be the repository passed to
	 *            {@link #open(Repository, URIish)}.
	 * @param uri
	 *            the URI used to access the remote repository. This must be the
	 *            URI passed to {@link #open(Repository, URIish)}.
	 */
	protected Transport(final Repository local, final URIish uri) {
		this.local = local;
		this.uri = uri;
	}

	/**
	 * Get the URI this transport connects to.
	 * <p>
	 * Each transport instance connects to at most one URI at any point in time.
	 * 
	 * @return the URI describing the location of the remote repository.
	 */
	public URIish getURI() {
		return uri;
	}

	/**
	 * Get the name of the remote executable providing upload-pack service.
	 * 
	 * @return typically "git-upload-pack".
	 */
	public String getOptionUploadPack() {
		return optionUploadPack;
	}

	/**
	 * Set the name of the remote executable providing upload-pack services.
	 * 
	 * @param where
	 *            name of the executable.
	 */
	public void setOptionUploadPack(final String where) {
		if (where != null && where.length() > 0)
			optionUploadPack = where;
		else
			optionUploadPack = RemoteConfig.DEFAULT_UPLOAD_PACK;
	}

	/**
	 * Get the description of how annotated tags should be treated during fetch.
	 * 
	 * @return option indicating the behavior of annotated tags in fetch.
	 */
	public TagOpt getTagOpt() {
		return tagopt;
	}

	/**
	 * Set the description of how annotated tags should be treated on fetch.
	 * 
	 * @param option
	 *            method to use when handling annotated tags.
	 */
	public void setTagOpt(final TagOpt option) {
		tagopt = option != null ? option : TagOpt.AUTO_FOLLOW;
	}

	/**
	 * Default setting is: {@link #DEFAULT_FETCH_THIN}
	 *
	 * @return true if fetch should request thin-pack when possible; false
	 *         otherwise
	 * @see PackTransport
	 */
	public boolean isFetchThin() {
		return fetchThin;
	}

	/**
	 * Set the thin-pack preference for fetch operation. Default setting is:
	 * {@link #DEFAULT_FETCH_THIN}
	 *
	 * @param fetchThin
	 *            true when fetch should request thin-pack when possible; false
	 *            when it shouldn't
	 * @see PackTransport
	 */
	public void setFetchThin(final boolean fetchThin) {
		this.fetchThin = fetchThin;
	}

	/**
	 * Default setting is: {@value RemoteConfig#DEFAULT_RECEIVE_PACK}
	 *
	 * @return remote executable providing receive-pack service for pack
	 *         transports.
	 * @see PackTransport
	 */
	public String getOptionReceivePack() {
		return optionReceivePack;
	}

	/**
	 * Set remote executable providing receive-pack service for pack transports.
	 * Default setting is: {@value RemoteConfig#DEFAULT_RECEIVE_PACK}
	 *
	 * @param optionReceivePack
	 *            remote executable, if null or empty default one is set;
	 */
	public void setOptionReceivePack(String optionReceivePack) {
		if (optionReceivePack != null && optionReceivePack.length() > 0)
			this.optionReceivePack = optionReceivePack;
		else
			this.optionReceivePack = RemoteConfig.DEFAULT_RECEIVE_PACK;
	}

	/**
	 * Default setting is: {@value #DEFAULT_PUSH_THIN}
	 *
	 * @return true if push should produce thin-pack in pack transports
	 * @see PackTransport
	 */
	public boolean isPushThin() {
		return pushThin;
	}

	/**
	 * Set thin-pack preference for push operation. Default setting is:
	 * {@value #DEFAULT_PUSH_THIN}
	 *
	 * @param pushThin
	 *            true when push should produce thin-pack in pack transports;
	 *            false when it shouldn't
	 * @see PackTransport
	 */
	public void setPushThin(final boolean pushThin) {
		this.pushThin = pushThin;
	}

	/**
	 * Fetch objects and refs from the remote repository to the local one.
	 * <p>
	 * This is a utility function providing standard fetch behavior. Local
	 * tracking refs associated with the remote repository are automatically
	 * updated if this transport was created from a {@link RemoteConfig} with
	 * fetch RefSpecs defined.
	 * 
	 * @param monitor
	 *            progress monitor to inform the user about our processing
	 *            activity. Must not be null. Use {@link NullProgressMonitor} if
	 *            progress updates are not interesting or necessary.
	 * @param toFetch
	 *            specification of refs to fetch locally. May be null or the
	 *            empty collection to use the specifications from the
	 *            RemoteConfig. Source for each RefSpec can't be null.
	 * @return information describing the tracking refs updated.
	 * @throws NotSupportedException
	 *             this transport implementation does not support fetching
	 *             objects.
	 * @throws TransportException
	 *             the remote connection could not be established or object
	 *             copying (if necessary) failed or update specification was
	 *             incorrect.
	 */
	public FetchResult fetch(final ProgressMonitor monitor,
			Collection<RefSpec> toFetch) throws NotSupportedException,
			TransportException {
		if (toFetch == null || toFetch.isEmpty()) {
			// If the caller did not ask for anything use the defaults.
			//
			if (fetch.isEmpty())
				throw new TransportException("Nothing to fetch.");
			toFetch = fetch;
		} else if (!fetch.isEmpty()) {
			// If the caller asked for something specific without giving
			// us the local tracking branch see if we can update any of
			// the local tracking branches without incurring additional
			// object transfer overheads.
			//
			final Collection<RefSpec> tmp = new ArrayList<RefSpec>(toFetch);
			for (final RefSpec requested : toFetch) {
				final String reqSrc = requested.getSource();
				for (final RefSpec configured : fetch) {
					final String cfgSrc = configured.getSource();
					final String cfgDst = configured.getDestination();
					if (cfgSrc.equals(reqSrc) && cfgDst != null) {
						tmp.add(configured);
						break;
					}
				}
			}
			toFetch = tmp;
		}

		final FetchResult result = new FetchResult();
		new FetchProcess(this, toFetch, tagopt).execute(monitor, result);
		return result;
	}

	/**
	 * Push objects and refs from the local repository to the remote one.
	 * <p>
	 * This is a utility function providing standard push behavior. It updates
	 * remote refs and send there necessary objects according to remote ref
	 * update specification. After successful remote ref update, associated
	 * locally stored tracking branch is updated if set up accordingly. Detailed
	 * operation result is provided after execution.
	 * <p>
	 * For setting up remote ref update specification from ref spec, see helper
	 * method {@link #findRemoteRefUpdatesFor(Collection)}, predefined refspecs ({@link #REFSPEC_TAGS},
	 * {@link #REFSPEC_PUSH_ALL}) or consider using directly
	 * {@link RemoteRefUpdate} for more possibilities.
	 *
	 * @see RemoteRefUpdate
	 *
	 * @param monitor
	 *            progress monitor to inform the user about our processing
	 *            activity. Must not be null. Use {@link NullProgressMonitor} if
	 *            progress updates are not interesting or necessary.
	 * @param toPush
	 *            specification of refs to push. May be null or the empty
	 *            collection to use the specifications from the RemoteConfig
	 *            converted by {@link #findRemoteRefUpdatesFor(Collection)}. No
	 *            more than 1 RemoteRefUpdate with the same remoteName is
	 *            allowed.
	 * @return information about results of remote refs updates, tracking refs
	 *         updates and refs advertised by remote repository.
	 * @throws NotSupportedException
	 *             this transport implementation does not support pusing
	 *             objects.
	 * @throws TransportException
	 *             the remote connection could not be established or object
	 *             copying (if necessary) failed at I/O or protocol level or
	 *             update specification was incorrect.
	 */
	public PushResult push(final ProgressMonitor monitor,
			Collection<RemoteRefUpdate> toPush) throws NotSupportedException,
			TransportException {
		if (toPush == null || toPush.isEmpty()) {
			// If the caller did not ask for anything use the defaults.
			toPush = findRemoteRefUpdatesFor(push);
			if (toPush.isEmpty())
				throw new TransportException("Nothing to push.");
		}
		final PushProcess pushProcess = new PushProcess(this, toPush);
		return pushProcess.execute(monitor);
	}

	/**
	 * Convert push remote refs update specification from {@link RefSpec} form
	 * to {@link RemoteRefUpdate}. Conversion expands wildcards by matching
	 * source part to local refs. expectedOldObjectId in RemoteRefUpdate is
	 * always set as null. Tracking branch is configured if RefSpec destination
	 * matches source of any fetch ref spec for this transport remote
	 * configuration.
	 *
	 * @param specs
	 *            collection of RefSpec to convert.
	 * @return collection of set up {@link RemoteRefUpdate}.
	 * @throws TransportException
	 *             when problem occurred during conversion or specification set
	 *             up: most probably, missing objects or refs.
	 */
	public Collection<RemoteRefUpdate> findRemoteRefUpdatesFor(
			final Collection<RefSpec> specs) throws TransportException {
		final List<RemoteRefUpdate> result = new LinkedList<RemoteRefUpdate>();
		final Collection<RefSpec> procRefs = expandPushWildcardsFor(specs);

		for (final RefSpec spec : procRefs) {
			try {
				final String srcRef = spec.getSource();
				// null destination (no-colon in ref-spec) is a special case
				final String remoteName = (spec.getDestination() == null ? spec
						.getSource() : spec.getDestination());
				final boolean forceUpdate = spec.isForceUpdate();
				final String localName = findTrackingRefName(remoteName);

				final RemoteRefUpdate rru = new RemoteRefUpdate(local, srcRef,
						remoteName, forceUpdate, localName, null);
				result.add(rru);
			} catch (TransportException x) {
				throw x;
			} catch (Exception x) {
				throw new TransportException(
						"Problem with resolving push ref spec \"" + spec
								+ "\" locally: " + x.getMessage(), x);
			}
		}
		return result;
	}

	/**
	 * Begins a new connection for fetching from the remote repository.
	 * 
	 * @return a fresh connection to fetch from the remote repository.
	 * @throws NotSupportedException
	 *             the implementation does not support fetching.
	 * @throws TransportException
	 *             the remote connection could not be established.
	 */
	public abstract FetchConnection openFetch() throws NotSupportedException,
			TransportException;

	/**
	 * Begins a new connection for pushing into the remote repository.
	 * 
	 * @return a fresh connection to push into the remote repository.
	 * @throws NotSupportedException
	 *             the implementation does not support pushing.
	 * @throws TransportException
	 *             the remote connection could not be established
	 */
	public abstract PushConnection openPush() throws NotSupportedException,
			TransportException;

	/**
	 * Close any resources used by this transport.
	 * <p>
	 * If the remote repository is contacted by a network socket this method
	 * must close that network socket, disconnecting the two peers. If the
	 * remote repository is actually local (same system) this method must close
	 * any open file handles used to read the "remote" repository.
	 */
	public abstract void close();

	private Collection<RefSpec> expandPushWildcardsFor(
			final Collection<RefSpec> specs) {
		final Map<String, Ref> localRefs = local.getAllRefs();
		final Collection<RefSpec> procRefs = new HashSet<RefSpec>();

		for (final RefSpec spec : specs) {
			if (spec.isWildcard()) {
				for (final Ref localRef : localRefs.values()) {
					if (spec.matchSource(localRef))
						procRefs.add(spec.expandFromSource(localRef));
				}
			} else {
				procRefs.add(spec);
			}
		}
		return procRefs;
	}

	private String findTrackingRefName(final String remoteName) {
		// try to find matching tracking refs
		for (final RefSpec fetchSpec : fetch) {
			if (fetchSpec.matchSource(remoteName)) {
				if (fetchSpec.isWildcard())
					return fetchSpec.expandFromSource(remoteName)
							.getDestination();
				else
					return fetchSpec.getDestination();
			}
		}
		return null;
	}
}
