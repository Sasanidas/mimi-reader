/*
 * Copyright (c) 2016. Eli Connelly
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.emogoth.android.phone.mimi.adapter;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.db.BoardTableConnection;
import com.emogoth.android.phone.mimi.db.DatabaseUtils;
import com.emogoth.android.phone.mimi.interfaces.MoveAndDismissable;
import com.emogoth.android.phone.mimi.util.MimiUtil;
import com.emogoth.android.phone.mimi.util.RxUtil;
import com.mimireader.chanlib.models.ChanBoard;

import java.util.List;
import java.util.concurrent.TimeUnit;

import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;


public class BoardListAdapter extends RecyclerView.Adapter<BoardListAdapter.BoardViewHolder> implements MoveAndDismissable {

    public static final String LOG_TAG = BoardListAdapter.class.getSimpleName();
    public static final boolean LOG_DEBUG = true;

    private LayoutInflater inflater;
    private List<ChanBoard> boards;

    private boolean editMode;
    private Context context;

    private View footer;
    private AdapterView.OnItemClickListener itemClickListener;
    private AdapterView.OnItemLongClickListener itemLongClickListener;
    private OnStartDragListener dragListener;

    private Subscription updateBoardsSubscription;
    private Subscription favoriteSubscription;
    private Subscription removeBoardSubscription;

    public BoardListAdapter(final Context context, final List<ChanBoard> boards) {
        init(context, boards);
    }

    private void init(final Context context, final List<ChanBoard> boards) {
        if (boards == null) {
            throw new IllegalStateException("boards object is null");
        }

        this.context = context;
        this.boards = boards;
        this.inflater = LayoutInflater.from(context);
    }

    public void setBoards(final List<ChanBoard> boards) {
        this.boards.clear();
        this.boards.addAll(boards);
        notifyDataSetChanged();
    }

    public List<ChanBoard> getBoards() {
        return boards;
    }

    public void removeBoard(int item) {
        final String boardName = boards.get(item).getName();

        boards.remove(item);
        notifyDataSetChanged();

        RxUtil.safeUnsubscribe(removeBoardSubscription);
        removeBoardSubscription = BoardTableConnection.setBoardVisibility(boardName, false)
                .compose(DatabaseUtils.<Boolean>applySchedulers())
                .subscribe();
    }

    public void setFooter(View footer) {
        this.footer = footer;
    }

    @Override
    public BoardViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.board_list_item, parent, false);
        return new BoardViewHolder(v);
    }

    @Override
    public void onBindViewHolder(final BoardViewHolder holder, int position) {
        final ChanBoard board = boards.get(position);
        if (holder.boardName != null) {
            holder.boardName.setVisibility(View.GONE);
        }

        if (holder.boardTitle != null) {
            holder.boardTitle.setText(board.getTitle());
        }

        if (holder.dragHandle != null) {
            holder.dragHandle.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (dragListener != null) {
                        if (MotionEventCompat.getActionMasked(event) ==
                                MotionEvent.ACTION_DOWN) {
                            dragListener.onStartDrag(holder);
                        }
                    }
                    return false;
                }
            });

            if (editMode) {
                holder.dragHandle.setVisibility(View.VISIBLE);
            } else {
                holder.dragHandle.setVisibility(View.GONE);
            }
        }

        if (holder.favorite != null) {
            if (editMode) {
                holder.favorite.setVisibility(View.VISIBLE);
            } else {
                holder.favorite.setVisibility(View.GONE);
            }

            if (board.isFavorite()) {
                holder.favorite.setText(R.string.ic_favorite_set);
            } else {
                holder.favorite.setText(R.string.ic_favorite_unset);
            }
            holder.favorite.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    final TextView checkBox = (TextView) v;
                    final boolean isFavorite = !checkBox.getText().equals(context.getString(R.string.ic_favorite_set));

                    RxUtil.safeUnsubscribe(favoriteSubscription);
                    favoriteSubscription = BoardTableConnection.setBoardFavorite(board.getName(), isFavorite)
                            .onErrorReturn(new Func1<Throwable, Boolean>() {
                                @Override
                                public Boolean call(Throwable throwable) {
                                    Log.e(LOG_TAG, "Error setting board favorite: board=" + board.getName() + ", favorite=" + isFavorite, throwable);
                                    return false;
                                }
                            })
                            .compose(DatabaseUtils.<Boolean>applySchedulers())
                            .subscribe(new Action1<Boolean>() {
                                @Override
                                public void call(Boolean success) {
                                    if (success) {
                                        board.setFavorite(isFavorite);
                                    } else {
                                        if (isFavorite) {
                                            checkBox.setText(R.string.ic_favorite_unset);
                                        } else {
                                            checkBox.setText(R.string.ic_favorite_set);
                                        }
                                    }
                                }
                            });

                    if (isFavorite) {
                        checkBox.setText(R.string.ic_favorite_set);
                    } else {
                        checkBox.setText(R.string.ic_favorite_unset);
                    }
                }
            });
        }

        if (itemClickListener != null && holder.root != null) {
            if (editMode) {
                holder.root.setOnClickListener(null);
            } else {
                holder.root.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!editMode) {
                            itemClickListener.onItemClick(null, holder.root, holder.getAdapterPosition(), 0);
                        }
                    }
                });
            }
        }

        if (itemLongClickListener != null && holder.root != null) {
            if (editMode) {
                holder.root.setOnLongClickListener(null);
            } else {
                holder.root.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (!editMode) {
                            itemLongClickListener.onItemLongClick(null, holder.root, holder.getAdapterPosition(), 0);
                        }
                        return true;
                    }
                });
            }
        }
    }

    @Override
    public long getItemId(final int position) {
        if (boards != null && position < boards.size()) {
            final ChanBoard board = boards.get(position);
            final String s = board.getName() + board.getTitle();
            return s.hashCode();
        }

        return -1;
    }

    @Override
    public int getItemCount() {
        return boards.size() + (footer != null ? 1 : 0);
    }

    @Override
    public void onItemMove(int from, int to) {
        final ChanBoard item = boards.set(from, boards.get(to));
        boards.set(to, item);

        RxUtil.safeUnsubscribe(updateBoardsSubscription);
        updateBoardsSubscription = BoardTableConnection.updateBoardOrder(boards)
                .delay(500, TimeUnit.MILLISECONDS)
                .compose(DatabaseUtils.<Boolean>applySchedulers())
                .subscribe(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean success) {
                        if (success) {
                            MimiUtil.setBoardOrder(context, 7);
                        }
                    }
                });

        notifyDataSetChanged();
    }

    @Override
    public void onDismiss(int position) {
        removeBoard(position);
    }

    public void editMode(final boolean enabled) {
        editMode = enabled;

        notifyDataSetChanged();
    }

    public boolean IsEditMode() {
        return editMode;
    }

    public void setDragListener(OnStartDragListener listener) {
        dragListener = listener;
    }

    public void setOnItemClickListener(AdapterView.OnItemClickListener listener) {
        itemClickListener = listener;
    }

    public void setOnItemLongClickListener(AdapterView.OnItemLongClickListener listener) {
        itemLongClickListener = listener;
    }

    public static class BoardViewHolder extends RecyclerView.ViewHolder {
        private final View root;
        private final TextView boardName;
        private final TextView boardTitle;
        private final View dragHandle;
        private final TextView favorite;

        public BoardViewHolder(final View root) {
            super(root);
            this.root = root;

            boardName = (TextView) root.findViewById(R.id.board_name);
            boardTitle = (TextView) root.findViewById(R.id.board_title);
            dragHandle = root.findViewById(R.id.drag_handle);
            favorite = (TextView) root.findViewById(R.id.favorite);

        }
    }

    public interface OnStartDragListener {

        /**
         * Called when a view is requesting a start of a drag.
         *
         * @param viewHolder The holder of the view to drag.
         */
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }
}
